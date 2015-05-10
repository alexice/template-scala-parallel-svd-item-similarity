# SVD Item Similarity Engine Template

## Documentation

Please refer to http://docs.prediction.io/templates/???/quickstart/

## Versions

### v0.0.1

Every item is represented as a set of attributes. Attributes can be of type 
String, Array[String], Int. The problem is that there is no unified method to
merge categorical and numeric attributes. In this template numeric attributes
are first scaled and then additional weights are applied to them, these  
weights can be configured in engine.json 

 
