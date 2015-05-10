# SVD Item Similarity Engine Template

## Documentation

__Not yet ready__

## Versions

### v0.0.1

Every item is represented as a set of attributes. Attributes can be of type 
String, Array[String], Int. The problem is that there is no unified method to
merge categorical and numeric attributes. In this template numeric attributes
are first scaled and then additional weights are applied to them, these  
weights can be configured in engine.json. Categorical attributed are encoded 
using one-hot encoder.

Data is generated from movielens database. Genre and year is real, actor 
list, director, producer and film duration in minutes are randomly generated 
for demo purposes.  

For similarity measure cosine similarity is used. It's calculated on reduced 
matrix obtained with SVD.