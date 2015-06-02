package org.template

import io.prediction.controller.{P2LAlgorithm, Params}
import io.prediction.data.storage.BiMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.feature.{Normalizer, StandardScaler}
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

import scala.reflect.ClassTag

class Model(val itemIds: BiMap[String, Int], val projection: DenseMatrix)
  extends Serializable {
  override def toString = s"Items: ${itemIds.size}"
}

case class AlgorithmParams(dimensions: Int, yearWeight: Double,
                           durationWeight: Double) extends Params


class Algorithm(val ap: AlgorithmParams)
  // extends PAlgorithm if Model contains RDD[]
  extends P2LAlgorithm[PreparedData, Model, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  private def encode(data: RDD[Array[String]]): RDD[Vector] = {
    val dict = BiMap.stringLong(data.flatMap(x => x))
    val len = dict.size

    data.map { sample =>
      val indexes = sample.map(dict(_).toInt).sorted
      Vectors.sparse(len, indexes, Array.fill[Double](indexes.length)(1.0))
    }
  }

  // [X: ClassTag] - trick to have multiple definitions of encode, they both
  // for RDD[_]
  private def encode[X: ClassTag](data: RDD[String]): RDD[Vector] = {
    val dict = BiMap.stringLong(data)
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


  private def transposeRDD(data: RDD[Vector]) = {
    val len = data.count().toInt

    val byColumnAndRow = data.zipWithIndex().flatMap {
      case (rowVector, rowIndex) => { rowVector match {
        case SparseVector(_, columnIndices, values) =>
          values.zip(columnIndices)
        case DenseVector(values) =>
          values.zipWithIndex
      }} map {
        case(v, columnIndex) => columnIndex -> (rowIndex, v)
      }
    }

    val byColumn = byColumnAndRow.groupByKey().sortByKey().values

    val transposed = byColumn.map {
      indexedRow =>
        val all = indexedRow.toArray.sortBy(_._1)
        val significant = all.filter(_._2 != 0)
        Vectors.sparse(len, significant.map(_._1.toInt), significant.map(_._2))
    }

    transposed
  }

  def train(sc: SparkContext, data: PreparedData): Model = {
    val itemIds = BiMap.stringInt(data.items.map(_._1))

    /**
     * Encode categorical vars
     * We use here one-hot encoding
     */

    val categorical = Seq(encode(data.items.map(_._2.director)),
      encode(data.items.map(_._2.producer)),
      encode(data.items.map(_._2.genres)),
      encode(data.items.map(_._2.actors)))

    /**
     * Transform numeric vars.
     * In our case categorical attributes are binary encoded. Numeric vars are
     * scaled and additional weights are given to them. These weights should be
     * selected from some a-priory information, i.e. one should check how
     * important year or duration for model quality and then assign weights
     * accordingly
     */

    val numericRow = data.items.map(x => Vectors.dense(x._2.year, x._2
      .duration))
    val weights = Array(ap.yearWeight, ap.durationWeight)
    val scaler = new StandardScaler(withMean = true,
      withStd = true).fit(numericRow)
    val numeric = numericRow.map(x => Vectors.dense(scaler.transform(x).
      toArray.zip(weights).map { case (x, w) => x * w }))

    /**
     * Now we merge all data and normalize vectors so that they have unit norm
     * and their dot product would yield cosine between vectors
     */

    val normalizer = new Normalizer()
    val allData = (categorical ++ Seq(numeric)).reduce(merge).map(x =>
      normalizer.transform(x))

    /**
     * Now we need to transpose RDD because SVD better works with ncol << nrow
     * and it's often the case when number of binary attributes is much greater
     * then the number of items. But in the case when the number of items is
     * more than number of attributes it is better not to transpose. In such
     * case U matrix should be used
     */


    val transposed = transposeRDD(allData)

    val matT: RowMatrix = new RowMatrix(transposed)

    // Make SVD to reduce data dimensionality
    val svdT: SingularValueDecomposition[RowMatrix, Matrix] = matT.computeSVD(
      ap.dimensions, computeU = false)

    val V: DenseMatrix = new DenseMatrix(svdT.V.numRows, svdT.V.numCols,
      svdT.V.toArray)

    val projection = Matrices.diag(svdT.s).multiply(V.transpose)

/*
    // This is an alternative code for the case when data matrix is not
    // transposed (when the number of items is much bigger then the number
    // of binary attributes

    val mat: RowMatrix = new RowMatrix(allData)

    val svd: SingularValueDecomposition[RowMatrix, Matrix] = mat.computeSVD(
      ap.dimensions, computeU = true)

    val U: DenseMatrix = new DenseMatrix(svd.U.numRows.toInt, svd.U.numCols
      .toInt, svd.U.rows.flatMap(_.toArray).collect(), isTransposed = true)

    val projection = Matrices.diag(svd.s).multiply(U.transpose)
*/
    
    new Model(itemIds, projection)
  }

  def predict(model: Model, query: Query): PredictedResult = {
    /**
     * Here we compute similarity to group of items in very simple manner
     * We just take top scored items for all query items

     * It is possible to use other grouping functions instead of max
     */

    val result = query.items.flatMap { itemId =>
      model.itemIds.get(itemId).map { j =>
        val d = for(i <- 0 until model.projection.numRows) yield model.projection(i, j)
        val col = model.projection.transpose.multiply(new DenseVector(d.toArray))
        for(k <- 0 until col.size) yield new ItemScore(model.itemIds.inverse
          .getOrElse(k, default="NA"), col(k))
      }.getOrElse(Seq())
    }.groupBy {
      case(ItemScore(itemId, _)) => itemId
    }.map(_._2.max).filter {
      case(ItemScore(itemId, _)) => !query.items.contains(itemId)
    }.toArray.sorted.reverse.take(query.num)

    if(result.isEmpty) logger.info(s"No prediction for items ${query.items}.")
    PredictedResult(result)
  }
}
