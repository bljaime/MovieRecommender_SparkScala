package advancedExerc

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._
import scala.io.Source
import java.nio.charset.CodingErrorAction
import scala.io.Codec
import scala.math.sqrt

object MovieSimilarities {
  
  /** Load up a Map of movie IDs to movie names. */
  def loadMovieNames() : Map[Int, String] = {
    
    // Handle character encoding issues:
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Create a Map of Ints to Strings, and populate it from u.item
    var movieNames:Map[Int, String] = Map()
    
     val lines = Source.fromFile("../ml-100k/u.item").getLines()
     for (line <- lines) {
       var fields = line.split('|')
       if (fields.length > 1) {
        movieNames += (fields(0).toInt -> fields(1))
       }
     }
    
     return movieNames
  }
  
  type MovieRating = (Int, Double)
  type UserRatingPair = (Int, (MovieRating, MovieRating))
  def makePairs(userRatings:UserRatingPair) = {
    val movieRating1 = userRatings._2._1
    val movieRating2 = userRatings._2._2
    
    val movie1 = movieRating1._1
    val rating1 = movieRating1._2
    val movie2 = movieRating2._1
    val rating2 = movieRating2._2
    
    ((movie1, movie2), (rating1, rating2))
  }
  
  def filterDuplicates(userRatings:UserRatingPair):Boolean = {
    
    val movieRating1 = userRatings._2._1  // _2: second element of the tuple (subtuple), _1: first element
    val movieRating2 = userRatings._2._2
    
    val movie1 = movieRating1._1  // 1st film ID
    val movie2 = movieRating2._1  // 2nd film ID
    
    return movie1 < movie2
  }
  
  type RatingPair = (Double, Double)
  type RatingPairs = Iterable[RatingPair]
  
  def computeCosineSimilarity(ratingPairs:RatingPairs): (Double, Int) = {
    var numPairs:Int = 0
    var sum_xx:Double = 0.0
    var sum_yy:Double = 0.0
    var sum_xy:Double = 0.0
    
    for (pair <- ratingPairs) {
      val ratingX = pair._1
      val ratingY = pair._2
      
      sum_xx += ratingX * ratingX
      sum_yy += ratingY * ratingY
      sum_xy += ratingX * ratingY
      numPairs += 1
    }
    
    val numerator:Double = sum_xy
    val denominator = sqrt(sum_xx) * sqrt(sum_yy)
    
    var score:Double = 0.0
    if (denominator != 0) {
      score = numerator / denominator
    }
    
    return (score, numPairs)
  }
  
  /** Our main function where the action happens */
  def main(args: Array[String]) {
    
    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)
    
    // STANDALONE - Create a SparkContext using every core of the local machine
    val sc = new SparkContext("local[*]", "MovieSimilarities")
    
    println("\nLoading movie names...")
    val nameDict = loadMovieNames()
    
    val data = sc.textFile("../ml-100k/u.data")

    // Map ratings to key / value pairs: user ID => movie ID, rating
    val ratings = data.map(l => l.split("\t")).map(l => (l(0).toInt, (l(1).toInt, l(2).toDouble)))
    
    // Map ratings to key / value pairs: user ID => movie ID, rating
    val IDs_ratings = data.map(l => l.split("\t")).map(l => (l(1).toInt, l(2).toDouble))
    val totalsAvg = IDs_ratings.mapValues(x => (x, 1)).reduceByKey( (x,y) => (x._1 + y._1, x._2 + y._2))  // (movie ID, (sum rates, rate instances))
    val averagesByMovieID = totalsAvg.mapValues(x => x._1 / x._2)  // AvgRate: totalFriends / totalInstances for each age
    
    // Emit every movie rated together by the same user. Self-join to find every combination:
    // If the user watched movies A, B and C, it will return a pair for AB, a pair for AC, BA, CA, and even AA, BB, CC
    val joinedRatings = ratings.join(ratings)  // default inner
    
    // At this point our RDD consists of userID => ((movieID, rating), (movieID, rating))
    
    // Filter out duplicate pairs, AB and BA e.g., and also forces first film ID to be smaller than second film ID
    val uniqueJoinedRatings = joinedRatings.filter(filterDuplicates)

    // Now key by (movie1, movie2) pairs, value: (ratingmovie1, ratingmovie2)
    val moviePairs = uniqueJoinedRatings.map(makePairs)

    // We now have (movie1, movie2) => (rating1, rating2)
    // Now collect all ratings for each movie pair and compute similarity
    val moviePairRatings = moviePairs.groupByKey()

    // We now have (movie1, movie2) = > (rating1, rating2), (rating1, rating2) ...
    // Can now compute similarities.
    val moviePairSimilarities = moviePairRatings.mapValues(computeCosineSimilarity).cache()
    
    // Extract similarities for the movie we care about that are "good".
    
    if (args.length > 0) {
      val scoreThreshold = 0.925  // Only movies with at least 0.925 similarity count
      val coOccurenceThreshold = 50.0  // Only movies watched by at least 50 people count
      
      val movieID:Int = args(0).toInt  // Passing as argument
      
      // Filter for movies with this sim that are "good" as defined by our quality thresholds above     
      
      val filteredResults = moviePairSimilarities.filter( x =>
        {
          val pair = x._1  // Pair of movie IDs
          val sim = x._2  // Pair (score, #peopleWhoRated)
          (pair._1 == movieID || pair._2 == movieID) && sim._1 > scoreThreshold && sim._2 > coOccurenceThreshold
        }
      )
        
      // Sort by quality score
      val results = filteredResults.map( x => (x._2, x._1)).sortByKey(false).take(10)
      
      // Force it to only show movies with more than 3.75 score, as we don't want it to show poorly rated movies
      val rateTreshold = 3.75
      
      println("\nTop 10 similar movies for " + nameDict(movieID))
      for (result <- results) {
        val sim = result._1
        val pair = result._2

        var similarMovieID = pair._1
        if (similarMovieID == movieID) {  // Determinate which one is the similar movie and the original movie in the pair
          similarMovieID = pair._2
        }
        
        val rec_avgRate = averagesByMovieID.lookup(similarMovieID)(0)
        
        if (rec_avgRate > rateTreshold)  // Quality filtering
          println(nameDict(similarMovieID) + "\tscore: " + sim._1 + "\tstrength: " + sim._2 + "\tavg. rate: " + rec_avgRate)
      }
      
    }
  }
}