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
        val unidadeDeMedidade: String = properties.get[String]("unidadeDeMedida")
        val linhaDeProdutoDescricao: String = properties.get[String]("linhaDeProdutoDescricao")
        val familiaDescricao: String = properties.get[String]("familiaDescricao")
        val empresaDescricao: String = properties.get[String]("empresaDescricao")
        val descricao: String = properties.get[String]("descricao")


        Item(entityId, unidadeDeMedidade, empresaDescricao, linhaDeProdutoDescricao, familiaDescricao,
          descricao)
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

case class Item(item: String, unidadeDeMedida: String, linhaDeProdutoDescricao: String, familiaDescricao: String, empresaDescricao:
                    String, descricao: String)

class TrainingData(val items: RDD[(String, Item)]) extends Serializable {
  override def toString = {
    s"items: [${items.count()}] (${items.take(2).toList}...)"
  }
}