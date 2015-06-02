# SVD Item Similarity Engine Template

Template to calculate similarity between items based on their attributes. 
Attributes can be either numeric or categorical in the last case it will be 
encoded using one-hot encoder. Algorithm uses SVD in order to reduce data 
dimensionality. Cosine similarity is now implemented but can be easily 
extended to other similarity measures.

## Overview

This template is a simple example of unsupervised learning algorithm 
implementation. Given items with their attributes of numeric and string types
(like movie release year, duration, director, actors, etc.) we can measure 
similarity between items based on some metric (cosine similarity in this 
example). 

Every item is represented as a set of attributes. Attributes can be of type 
String, Array of Strings and Int. Items are transformed into vectors with 
categorical variables encoded using one-hot encoder 
(http://en.wikipedia.org/wiki/One-hot) i.e. every String and Array of Strings
attribute is transformed to number of binary attributes (facts about item) 
like "Is John Carpenter the director of the movie?".
So item vector length is equal to number of possible values of all categorical 
variables plus the number of numeric variables. 
 
Given such encoding of data numeric and categorical variables are not 
equivalently contribute into resulting vector (the problem is that there is 
no unified method to merge categorical and numeric attributes.). To fix this 
issue in some extent weights of numeric variables are used in this example. 

Then vectors are normalized. So that their dot product produces cosine of the
angle between vectors or in other words item-attribute matrix multiplied to 
itself transposed produces cosine similarity matrix (item to item). If we 
take into consideration that the number of attributes and items can be very 
big, then such matrix can be very big as well and it can be computationally 
difficult to calculate it, so dimensionality reduction should be used. In 
this example Singular value decomposition 
(https://spark.apache.org/docs/latest/mllib-dimensionality-reduction.html#singular-value-decomposition-svd) is used. So we need to save one 
matrix of size M x K, M is the number of items and K is the number of 
dimensions we're going to use.

Data is generated from Movielens database. Genre and year is real, actor 
list, director, producer and film duration in minutes are randomly generated 
for demo purposes.  

## Engine parameters

This example has three parameters to tune. The main is __dimensions__, this 
parameter should be less or equal to min(number of items, item vector 
dimension) and represents dimensionality of the space we are projecting our 
vectorized items. This parameter should be chosen so that our model could 
grasp the data (similarity between items) from one side and were not very 
excessive from the other side. 

The two other parameters are __yearWeight__ and __durationWeight__. They 
represent weight of numeric attributes year and duration respectively. These 
parameters should be selected experimentally so that model would represent 
better similarity between items. No unified recipe can be offered for 
selecting them, but some intuition can be used. As both year and duration are 
scaled to zero mean and unit standard deviation their weights represent their
relative importance to any other attribute.

## Versions

### v0.0.3

### v0.0.2

### v0.0.1

## Usage

### Event Data Requirements

By default, the template requires the following events to be collected:

- item $set event, which sets the attributes of the item

### Input Query

- array of items
- number of results

### Output PredictedResult

- List of items with similarity score


## Import sample data

```
$ cd data
$ python import_eventserver.py --access_key <your_access_key>
```

The sample data for training is generated from four files:

- sample_actors.txt
- sample_directors.txt
- sample_producers.txt
- sample_movies.txt

## Build, train, deploy

As for most prediction.io templates the workflow is standard:

```
$ pio build && pio train && pio deploy
```

## Query

```
$ cd data
$ python send_query.py
```
