package org.template

import io.prediction.controller.EngineFactory
import io.prediction.controller.Engine

// Query most similar (top num) items to the given
case class Query(item: String, num: Int) extends Serializable

case class PredictedResult(itemScores: Array[ItemScore]) extends Serializable

case class ItemScore(item: String, score: Double) extends Serializable

object SVDItemSimilarityEngine extends EngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("algo" -> classOf[Algorithm]),
      classOf[Serving])
  }
}