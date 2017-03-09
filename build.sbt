import AssemblyKeys._

assemblySettings

name := "template-scala-parallel-svd-item-similarity"

organization := "io.prediction"

libraryDependencies ++= Seq(
  "org.apache.predictionio"    %% "apache-predictionio-core" % "0.10.0-incubating" % "provided",
  "org.apache.spark"           %% "spark-core"    % "2.1.0"  % "provided",
  "org.apache.spark"           %% "spark-mllib"   % "2.1.0"  % "provided",
  "org.xerial.snappy"          % "snappy-java"    % "1.1.1.7")

