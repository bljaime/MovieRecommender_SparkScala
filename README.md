# Movie recommender using Item-based collaborative filtering 

A simple Movie recommender using Item-based collaborative filtering, with Spark and Scala.

## Context

To develop this application, the [MovieLens](https://grouplens.org/datasets/movielens/) dataset is used, which consists of 100,000 ratings from 1000 users on 1700 movies pre-year 1998. In particular, the *u.item* file (which is responsible for mapping between the IDs and titles of the movies) and the *u.data* file (which contains user IDs, movie IDs, ratings and timestamps).

The source code file itself is properly commented, so there's no need to add much more here. This particular implementation corresponds to Spark's **standalone mode**, assuming that *MovieSimilarities.scala* is the source file and *MS.jar* the corresponding jar file. When executing it, it is necessary to pass as argument the ID of the movie from which you want to obtain a recommendation, as it is done next:

```bash
> spark-submit --class advancedExerc.MovieSimilarities MS.jar 50
```
This is what would be shown, in the case of the Star Wars movie, which corresponds to the argument 50 above (movie ID): <p align="center"> <img src="/img/sc.PNG"/>
  
We can see that the results are fairly accurate, as the first recommendations correspond to two other films in the Star Wars saga, with which the similarity **score** is quite high.  Moreover, our recommendator will only recommend films with an average rating above the 3.75 threshold, so it will recommend only decent-quality films.

## Acknowledgements

Thanks to [Frank Kane](https://www.linkedin.com/in/fkane/), as this project has been developed in the framework of *Apache Spark 2 with Scala - Hands On with Big Data!* course.