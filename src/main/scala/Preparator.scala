package org.template

import io.prediction.controller.PPreparator
import io.prediction.data.storage.Event

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD


class Preparator extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
//    new PreparedData(itemData = trainingData.itemData.sample(true, 0.06))
    new PreparedData(itemData = trainingData.itemData)
  }
}

class PreparedData(val itemData: RDD[ItemInfo]) extends Serializable