/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.clustering

import scala.collection.mutable
import smile.math.distance.Distance

/**
 * @author Beck Gaël
 * KMeans Model that keeps cluster centroids and cardinalities. And offer prediction functionalities
 **/
class KModesModel(val centroids: mutable.HashMap[Int, Array[Int]], val cardinalities: mutable.HashMap[Int, Int], val metric: Distance[Array[Int]]) {

	type ClusterID = Int
	type BinaryVector = Array[Int]

	/**
	 * Return the nearest mode for a specific point
	 **/
	def predict(v: Array[Int]): ClusterID = {
		centroids.toArray.map{ case(clusterID, centroid) => (clusterID, metric.d(centroid, v)) }.sortBy(_._2).head._1
	}

	/**
	 * Return the nearest mode for a dataset
	 **/
	def predict(data: Seq[Array[Int]]): Seq[(ClusterID, BinaryVector)] = {
		val centroidsAsArray = centroids.toArray

		def predictCluster(v: Array[Int]) = {
			centroidsAsArray.map{ case(clusterID, centroid) => (clusterID, metric.d(centroid, v)) }.sortBy(_._2).head._1
		}

		data.map( v => (predictCluster(v), v) )
	}
}