package org.template

import io.prediction.controller.{PersistentModelLoader, PersistentModel, P2LAlgorithm, Params}

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.feature.{Normalizer, StandardScaler}
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

import scala.reflect.ClassTag

case class AlgorithmParams(dimensions: Int, yearWeight: Double,
                           durationWeight: Double) extends Params


class Algorithm(val ap: AlgorithmParams)
  // extends PAlgorithm if Model contains RDD[]
  extends P2LAlgorithm[PreparedData, Model, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  private def encode(data: RDD[Array[String]]): RDD[Vector] = {
    // Make category dictionary
    val dict = data.flatMap(x => x).distinct().zipWithIndex().collect().
      map(x => x._1 -> x._2).toMap
    val len = dict.size

    data.map { sample =>
      val indexes = sample.map(dict(_).toInt).sorted
      Vectors.sparse(len, indexes, Array.fill[Double](indexes.length)(1.0))
    }
  }

  // [X: ClassTag] - trick to have multiple definitions of encode, they both
  // for RDD[_]
  private def encode[X: ClassTag](data: RDD[String]): RDD[Vector] = {
    val dict = data.distinct().zipWithIndex().collect().map(x => x._1 -> x
      ._2).toMap
    val len = dict.size

    data.map { sample =>
      val index = dict(sample).toInt
      Vectors.sparse(len, Array(index), Array(1.0))
    }
  }

  private def merge(v1: RDD[Vector], v2: RDD[Vector]): RDD[Vector] = {
    v1.zip(v2) map {
      case (SparseVector(leftSz, leftInd, leftVals), SparseVector(rightSz,
      rightInd, rightVals)) =>
        Vectors.sparse(leftSz + rightSz, leftInd ++ rightInd.map(_ + leftSz),
          leftVals ++ rightVals)
      case (SparseVector(leftSz, leftInd, leftVals), DenseVector(rightVals)) =>
        Vectors.sparse(leftSz + rightVals.length, leftInd ++ (0 until rightVals
          .length).map(_ + leftSz), leftVals ++ rightVals)
    }
  }

  def train(sc: SparkContext, data: PreparedData): Model = {
    val itemIds = data.itemData.map(_.item).collect()

    // Encode categorical vars
    // We use here one-hot encoding
    val categorical = Seq(encode(data.itemData.map(_.director)),
      encode(data.itemData.map(_.producer)),
      encode(data.itemData.map(_.genres)),
      encode(data.itemData.map(_.actors)))

    // Transform numeric vars in this case categorical attributes are binary
    // encoded. Numeric vars are scaled and additional weights are given to
    // them. These weights should be selected from some a-priory information,
    // i.e. one should check how important year or duration for model quality
    // and then assign weights accordingly
    val numericRow = data.itemData.map(x => Vectors.dense(x.year, x.duration))
    val weights = Array(ap.yearWeight, ap.durationWeight)
    val scaler = new StandardScaler(withMean = true,
      withStd = true).fit(numericRow)
    val numeric = numericRow.map(x => Vectors.dense(scaler.transform(x).
      toArray.zip(weights).map { case (x, w) => x * w }))

    // Now we merge all data and normalize vectors so that they have unit norm
    // and their dot product would yield cosine between vectors
    val normalizer = new Normalizer()
    val allData = (categorical ++ Seq(numeric)).reduce(merge).map(x =>
      normalizer.transform(x))

    // Now we need to transpose RDD because SVD better works with ncol << nrow
    // and it's often the case when number of binary attributes is much greater
    // then the number of items
    val byColumnAndRow = allData.zipWithIndex().flatMap {
      case (rowVector, rowIndex) => rowVector.toArray.zipWithIndex.map {
        case (number, columnIndex) => columnIndex -> (rowIndex, number)
      }
    }

    val byColumn = byColumnAndRow.groupByKey().sortByKey().values

    val transposed = byColumn.map {
      indexedRow =>
        val all = indexedRow.toArray.sortBy(_._1)
        val significant = all.filter(_._2 != 0)
        Vectors.sparse(all.length, significant.map(_._1.toInt),
          significant.map(_._2))
    }

    val mat: RowMatrix = new RowMatrix(transposed)

    // Make SVD to reduce data dimensionality
    val svd: SingularValueDecomposition[RowMatrix, Matrix] = mat.computeSVD(
      ap.dimensions, computeU = false)
    val V: DenseMatrix = new DenseMatrix(svd.V.numRows, svd.V.numCols,
      svd.V.toArray)
    val s: Vector = svd.s

    // Calculating cosine distance between each item
    val tmp = Matrices.diag(Vectors.dense(s.toArray.map(x => x * x))).
      multiply(V.transpose)
    val cosines = V.multiply(tmp)

    val n = cosines.numCols


//    for(i <- 0 until n) logger.info(cosines(i, 0))
//    itemIds.foreach(v => logger.info(v))

    new Model(itemIds, cosines)
  }

  def predict(model: Model, query: Query): PredictedResult = {

    val col = model.itemIds.zipWithIndex.find(_._1 == query.item).map { x =>
      val idx = x._2
      for (j <- 0 until model.similarities.numCols)
        yield model.similarities(idx, j)
    }.getOrElse(Seq())

    val result = col.zip(model.itemIds).filter(x => x._2 != query.item).
      sortWith(_._1 > _._1).map(x => new ItemScore(x._2, x._1)).
      toArray.take(query.num)

    if(result.isEmpty) logger.info(s"No prediction for item ${query.item}.")
    PredictedResult(result)
  }
}

class Model(val itemIds: Array[String], val similarities: DenseMatrix)
  extends PersistentModel[AlgorithmParams] {
  override def toString = s"Items: ${itemIds.length}"
  def save(id: String, params: AlgorithmParams, sc: SparkContext): Boolean = {
    sc.parallelize(Seq(itemIds)).saveAsObjectFile(s"/tmp/${id}/itemIds")
    sc.parallelize(Seq(similarities)).saveAsObjectFile(s"/tmp/${id}/similarities")

    true
  }
}

object Model extends PersistentModelLoader[AlgorithmParams, Model] {
  def apply(id: String, params: AlgorithmParams, sc: Option[SparkContext]) = {
    // We can reconstruct distances on load in principle...
    new Model(
      itemIds = sc.get.objectFile[Array[String]](s"/tmp/${id}/itemIds").first,
      similarities = sc.get.objectFile[DenseMatrix](s"/tmp/${id}/similarities").first)
  }
}