package org.template

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
import io.prediction.data.storage.Event
import io.prediction.data.store.PEventStore

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceParams(appName: String) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {

    // read all events of EVENT involving ENTITY_TYPE and TARGET_ENTITY_TYPE
    val eventsRDD: RDD[Event] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("item"),
      eventNames = Some(List("infoEntered")))(sc)

    val itemDataRDD: RDD[ItemInfo] = eventsRDD.map { event =>
      val title: String = event.properties.get[String]("title")
      val producer: String = event.properties.get[String]("producer")
      val director: String = event.properties.get[String]("director")
      val genres: Array[String] = event.properties.get[Array[String]]("genres")
      val actors: Array[String] = event.properties.get[Array[String]]("actors")
      val year: Int = event.properties.get[Int]("year")
      val duration: Int = event.properties.get[Int]("duration")

      ItemInfo(event.entityId, title, year, duration, genres, producer,
        director, actors)
    }.cache()

    new TrainingData(itemDataRDD)
  }
}


// Array should be array of words
case class ItemInfo(item: String, title: String, year: Int, duration: Int,
                    genres: Array[String], producer: String, director:
                    String, actors: Array[String])

class TrainingData(val itemData: RDD[ItemInfo]) extends Serializable {
  override def toString = {
    s"items: [${itemData.count()}] (${itemData.take(2).toList}...)"
  }
}