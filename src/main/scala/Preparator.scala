package org.template

import org.apache.predictionio.controller.PPreparator
import org.apache.predictionio.data.storage.Event

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD


class Preparator extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
//    new PreparedData(items = trainingData.items.sample(true, 0.06))
    new PreparedData(items = trainingData.items)
  }
}

class PreparedData(val items: RDD[(String, Item)]) extends Serializable
