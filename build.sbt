import AssemblyKeys._

assemblySettings

name := "template-scala-parallel-svd-item-similarity"

organization := "io.prediction"

libraryDependencies ++= Seq(
  "io.prediction"    %% "core"          % "0.9.5" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.3.0" % "provided",
  "org.apache.spark" %% "spark-mllib"   % "1.3.0" % "provided",
  "org.xerial.snappy" % "snappy-java" % "1.1.1.7")