package org.template

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
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

    // create a RDD of (entityID, Item)
    val itemsRDD: RDD[(String, Item)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "item"
    )(sc).map { case (entityId, properties) =>
      val item = try {
        val title: String = properties.get[String]("title")
        val producer: String = properties.get[String]("producer")
        val director: String = properties.get[String]("director")
        val genres: Array[String] = properties.get[Array[String]]("genres")
        val actors: Array[String] = properties.get[Array[String]]("actors")
        val year: Int = properties.get[Int]("year")
        val duration: Int = properties.get[Int]("duration")

        Item(entityId, title, year, duration, genres, producer, director,
          actors)
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties ${properties} of" +
            s" item ${entityId}. Exception: ${e}.")
          throw e
        }
      }
      (entityId, item)
    }.cache()

    new TrainingData(items = itemsRDD)
  }
}

case class Item(item: String, title: String, year: Int, duration: Int,
                    genres: Array[String], producer: String, director:
                    String, actors: Array[String])

class TrainingData(val items: RDD[(String, Item)]) extends Serializable {
  override def toString = {
    s"items: [${items.count()}] (${items.take(2).toList}...)"
  }
}