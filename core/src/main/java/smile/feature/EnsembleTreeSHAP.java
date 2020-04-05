/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.feature;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import smile.base.cart.InternalNode;
import smile.base.cart.LeafNode;
import smile.base.cart.Node;
import smile.base.cart.RegressionNode;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.regression.RegressionTree;

/**
 * SHAP is a recently proposed algorithm for interpretable machine learning by
 *
 * <pre>
 * Lundberg, Scott M., and Su-In Lee. “A unified approach to interpreting model predictions.” Advances in Neural Information Processing Systems. 2017
 * </pre>
 *
 * and tree-SHAP is a specific version of implementation for tree-based machine learning like CART, Random Forest, Gradient Boost, etc.
 *
 * <pre>
 * Lundberg, Scott M., Gabriel G. Erion, and Su-In Lee. “Consistent individualized feature attribution for tree ensembles.” arXiv preprint arXiv:1802.03888 (2018)
 * </pre>
 *
 * implementation reference as below:
 *
 * <pre>
 * https://github.com/slundberg/shap/blob/master/shap/explainers/pytree.py
 * </pre>
 *
 * support only {@link RegressionTree} or ensemble based on it like {@link smile.regression.GradientTreeBoost}
 *
 * <p>
 *
 * @author ray
 * @see
 * <pre>
 * https://christophm.github.io/interpretable-ml-book/shap.html
 * </pre>
 */
public class EnsembleTreeSHAP {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnsembleTreeSHAP.class);
	
	/** data/tuple batch size in shap importance calculation concurrency */
	private static final int SHAP_IMPORTANCE_BATCH = 1000;

	/** max depth among all ensemble trees in the tranied model  */
	private int maxd;
	
	/** {@link RegressionTree} for shap value calculation from the trained model */
	private RegressionTree[] trees;

	/**
	 * constructor with tree ensemble
	 * @param trees {@link RegressionTree} ensemble for shap calculation
	 */
	public EnsembleTreeSHAP(RegressionTree[] trees) {
		this.trees = trees;

		for (RegressionTree t : this.trees) {
			if (t.root().depth() > maxd) {
				maxd = t.root().depth();
			}
		}

		// Preallocate space for the unique path data: 
		// split features, zero/one fractions, weights, etc
		maxd += 2;
	}

	/**
	 * return shap value for given data instance, note this might take a fairly
	 * large memory if the dataset is big.
	 * <p>
	 * This is useful if you want to have deep insight about effect of each feature
	 * over prediction by using returned shap value for every data instance to draw
	 * a summary plot between each feature and prediction
	 * 
	 * @param x data {@link Tuple}
	 */
	public double[] shapValues(Tuple x) {
		int s = maxd * (maxd + 1);
		
		int[] feature_indexes = initIntegers(s);
		double[] zero_fractions = initDoubles(s); 
		double[] one_fractions = initDoubles(s);
		double[] pweight = initDoubles(s);
		
		double[] shaps = shapValues(x, false, feature_indexes, zero_fractions, one_fractions, pweight);
		return shaps;
	}

	/**
	 * return shap value (possibly absolute value if chosen) for given data instance
	 * 
	 * @param x              data {@link Tuple}
	 * @param returnAbsolute true if you want the feature importance, just take
	 *                       absolute shap values for every instance, average by
	 *                       feature and then sort, default false
	 * @param feature_indexes split feature array along the tree traverse
	 * @param zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param pweights hold the proportion of sets of a given cardinality that are present
	 */
	private double[] shapValues(Tuple x, boolean returnAbsolute, 
			int[] feature_indexes,
			double[] zero_fractions, 
			double[] one_fractions,
			double[] pweights) {
		double[] phi = new double[x.schema().length()];
		phi = shapValues(phi, x, returnAbsolute, feature_indexes, zero_fractions, one_fractions, pweights);
		return phi;
	}
	
	/**
	 * return shap value (possibly absolute value if chosen) for given data instance
	 * 
	 * @param phi  shap value array to return
	 * @param x              data {@link Tuple}
	 * @param returnAbsolute true if you want the feature importance, just take
	 *                       absolute shap values for every instance, average by
	 *                       feature and then sort, default false
	 * @param feature_indexes split feature array along the tree traverse
	 * @param zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param pweights hold the proportion of sets of a given cardinality that are present
	 *                       
	 */
	private double[] shapValues(double[] phi, Tuple x, boolean returnAbsolute, 
			int[] feature_indexes,
			double[] zero_fractions, 
			double[] one_fractions,
			double[] pweights) {

		int treeLength = trees.length;
		for (int tIdx = 0; tIdx < treeLength; tIdx++) {
			try {
				treeShap(trees[tIdx], x, returnAbsolute, phi, 0.0, 0, feature_indexes, zero_fractions, one_fractions, pweights);
			} catch (Exception e) {
				String err = "error calculating shap for instance: " + x + " using " + tIdx + "th sub-tree.";
				logger.error(err, e);
				throw new RuntimeException(err, e);
			}
		}

		for (int i = 0; i < phi.length; i++) {
			phi[i] /= (double) treeLength;
		}

		if (returnAbsolute) {
			for (int i = 0; i < phi.length; i++) {
				phi[i] = Math.abs(phi[i]);
			}
		}

		return phi;
	}

	/**
	 * return the shap importance for the given model by taking absolute shap values
	 * for every instance and average by feature.
	 * <p>
	 * Note it might take a fairly long time if the dataset is big and the
	 * underlying model is complex (many sub-trees, many nodes, large depth, etc)
	 * 
	 * @param schema dataset schema
	 * @param data training data used to calculate shap values
	 * 
	 */
	public double[] shapImportance(StructType schema, DataFrame data) {
		double[] meanShap = new double[schema.length()];
		Arrays.setAll(meanShap, num -> 0.0);

		long startTime = System.currentTimeMillis();
		int concurrencyLevel = (data.size() / SHAP_IMPORTANCE_BATCH) + 1;		

		try {
			// avoid contending for default common forkjoin pool used in stream parallel
			ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

			logger.info("--- start calculation for data ---");
			forkJoinPool.submit(() -> 
			     IntStream.rangeClosed(1, concurrencyLevel).parallel().forEach(
				    range -> {
				       int startIdx = (range - 1) * SHAP_IMPORTANCE_BATCH;
				       int endIdx = (startIdx + SHAP_IMPORTANCE_BATCH);
				       endIdx = (endIdx > data.size() ? data.size() : endIdx);
					   logger.info("start shap value calculation for data tuples at indexes:[" + startIdx + "," + endIdx + ") using thread: " 
				                   + Thread.currentThread().getName());
				       
					   int s = maxd * (maxd + 1);
					   int[] feature_indexes = initIntegers(s);
					   double[] zero_fractions = initDoubles(s); 
					   double[] one_fractions = initDoubles(s);
					   double[] pweight = initDoubles(s);
				       
					   double[] means = new double[data.schema().length()];
					   Arrays.setAll(means, num -> 0.0);

					   double[] shapValues = new double[data.schema().length()];
						
					   for (int xi = startIdx; xi < endIdx; xi++) {
							if (((xi - startIdx) + 1) % SHAP_IMPORTANCE_BATCH == 0) {
							    // tracking for shap value calculation progress for data tuple 
								logger.debug("*");
							}
							Tuple xx = null;
							try {
								xx = data.get(xi);
								Arrays.setAll(shapValues, num -> 0.0);		
								
								shapValues = shapValues(shapValues, xx, true, feature_indexes, zero_fractions, one_fractions, pweight);
								for (int i = 0; i < means.length; i++) {
									means[i] += shapValues[i];
								}
							} catch (Exception e) {
								String err = "error calculating shap values for instance: " + xx;
								logger.error(err, e);
							}
					   }
					   
					   // update the final shap value result
					   synchronized(meanShap){
						   for (int i = 0; i < meanShap.length; i++) {
								meanShap[i] += means[i];
						   }
					   }
					   logger.info("complete shap value calculation for data instances at indexes:[" + startIdx + "," + endIdx + ")");	
				    }
			     )
			).get();

			logger.info("--- complete calculation for " + data.size() + " data, took " + (System.currentTimeMillis() - startTime) / 1000 + " seconds -----");
			
		} catch (Exception e) {
			String err = "error calculating shap values for dataframe.";
			logger.error(err, e);
			throw new RuntimeException(err, e);
		}

		logger.info("--- shap importance (unsorted) ---");
		for (int i = 0; i < meanShap.length; i++) {
			meanShap[i] /= (double) data.size();
			logger.info(String.format("%-15s %.4f%n", schema.fieldName(i), meanShap[i]));
		}

		return meanShap;
	}
	
	/** 
	 * pre-allocate double array for shap calcualtion 
	 * @param size usually at magnitude of square of max depth in ensemble
	 */
	private static double[] initDoubles(int size) {
		double[] ret = new double[size];
		Arrays.setAll(ret, num -> Double.MIN_VALUE);
		return ret;
	}
	
	/** 
	 * pre-allocate double array for shap calcualtion 
	 * @param size usually at magnitude of square of max depth in ensemble
	 */
	private static int[] initIntegers(int size) {
		int[] ret = new int[size];
		Arrays.setAll(ret, num -> Integer.MIN_VALUE);
		return ret;
	}

	/**
	 * calculate shap value for given tree and data tuple
	 * 
	 * @param tree RegressionTree to calculate shap vaue against
	 * @param x dat tuple to calculate shap value
	 * @param returnAbsolute if true, return absolute shap value
	 * @param phi shap values to return
	 * @param condition default 0, used to divide up the condition_fraction among the recursive calls
	 * @param condition_feature used to divide up the condition_fraction among the recursive calls
	 * @param feature_indexes split feature array along the tree traverse
	 * @param zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param pweights hold the proportion of sets of a given cardinality that are present
	 */
	private static void treeShap(RegressionTree tree, Tuple x, boolean returnAbsolute,
			double[] phi, double condition, int condition_feature, 
			int[] feature_indexes,
			double[] zero_fractions, 
			double[] one_fractions,
			double[] pweights) {
				
		// start the recursive algorithm
		treeShapRecursive(tree.root(), 
				x, phi, 0, 
				feature_indexes,
				zero_fractions, 
				one_fractions, 
				pweights, 
				1, 1, -1, condition,
				condition_feature, 1,
				tree.root().size(), 0);

		// reset following mutables for another tuples
		for (int i = 0; i < feature_indexes.length; i++) {
			feature_indexes[i] = Integer.MIN_VALUE;
			zero_fractions[i] = Double.MIN_VALUE;
			one_fractions[i] = Double.MIN_VALUE;
			pweights[i] = Double.MIN_VALUE;
		}
	}

	/**
	 * extend our tree path with a fraction of one and zero extensions
	 * 
	 * @param feature_indexes split feature array along the tree traverse
	 * @param zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param pweights hold the proportion of sets of a given cardinality that are present
	 * @param unique_depth new depth deep down the tree
	 * @param zero_fraction new fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fraction new fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param feature_index new split feature along the tree traverse
	 * @param offsetDepth indexing support sub-range operation
	 */
	private static void extendPath(int[] feature_indexes, 
			double[] zero_fractions,
			double[] one_fractions, 
			double[] pweights, 
			int unique_depth, 
			double zero_fraction,
			double one_fraction,
			int feature_index,
			int offsetDepth) {
		
		feature_indexes[unique_depth + offsetDepth] = feature_index;
		zero_fractions[unique_depth + offsetDepth] = zero_fraction;
		one_fractions[unique_depth + offsetDepth] = one_fraction;
		pweights[unique_depth + offsetDepth] = unique_depth == 0 ? 1.0 : 0.0;

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			 pweights[i + 1 + offsetDepth] += (one_fraction * pweights[i + offsetDepth] * (i + 1.0) / uniquePathPlus1);
			 pweights[i + offsetDepth] = zero_fraction * pweights[i + offsetDepth] * (unique_depth - i + 0.0) / uniquePathPlus1;
		}
	}

	/**
	 * undo a previous extension of the tree path
	 * 
	 * @param feature_indexes split feature array along the tree traverse
	 * @param zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param pweights hold the proportion of sets of a given cardinality that are present
	 * @param unique_depth new depth deep down the tree
	 * @param path_index indexing for which is to unwind path value
	 * @param offsetDepth indexing support sub-range operation
	 */
	private static void unwindPath(int[] feature_indexes, 
			double[] zero_fractions,
			double[] one_fractions, 
			double[] pweights, 
			int unique_depth, int path_index,
			int offsetDepth) {

		double one_fraction = one_fractions[path_index + offsetDepth];
		double zero_fraction = zero_fractions[path_index + offsetDepth];
		double next_one_portion = pweights[path_index + offsetDepth];

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			if (one_fraction != 0) {
				double tmp = pweights[i + offsetDepth];
				pweights[i + offsetDepth] = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
				next_one_portion = tmp - pweights[i + offsetDepth] * zero_fraction * (unique_depth - i + 0.0) / uniquePathPlus1;
			} else {
				pweights[i + offsetDepth] = (pweights[i + offsetDepth] * uniquePathPlus1) / (zero_fraction * (unique_depth - i + 0.0));
			}
		}

		for (int i = path_index; i < unique_depth; i++) {
			feature_indexes[i + offsetDepth] = feature_indexes[i + 1 + offsetDepth];
			zero_fractions[i + offsetDepth] = zero_fractions[i + 1 + offsetDepth];
			one_fractions[i + offsetDepth] = one_fractions[i + 1 + offsetDepth];
		}
	}

	/**
	 * determine what the total permutation weight would be if we unwound a previous extension in the tree path
	 * 
	 * @param zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param pweights hold the proportion of sets of a given cardinality that are present
	 * @param unique_depth new depth deep down the tree
	 * @param path_index indexing for which is to unwind path value
	 * @param offsetDepth indexing support sub-range operation
	 * 
	 * @return the total permutation weight would be if we unwound a previous extension in the tree path
	 */
	private static double unwoundPathSum(double[] zero_fractions, double[] one_fractions,
			double[] pweights, int unique_depth, int path_index,
			int offsetDepth) {

		double one_fraction = one_fractions[path_index + offsetDepth];
		double zero_fraction = zero_fractions[path_index + offsetDepth];
		double next_one_portion = pweights[path_index + offsetDepth];
		double total = 0;

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			if (one_fraction != 0) {
				double tmp = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
				total += tmp;
				next_one_portion = pweights[i + offsetDepth] - tmp * zero_fraction * ((unique_depth - i + 0.0) / uniquePathPlus1);
			} else {
				double numerator = pweights[i + offsetDepth] / zero_fraction;
				double denominator = (unique_depth - i + 0.0) / uniquePathPlus1;
				total += (numerator / denominator);
			}
		}

		return total;
	}
	
	/**
	 * replicate inside the given double array from [offsetDepth, offsetDepth + unique_depth + 1)
	 * to range with index starting at offsetDepth + unique_depth + 1
	 * 
	 * @param original double array to replicate in place
	 * @param unique_depth replicate size = unique_depth + 1
	 * @param offsetDepth indexing support sub-range operation
	 * 
	 * @return original array after in-place replication
	 */
	private static double[] replicateInPlace(double[] original, int unique_depth, int offsetDepth) {
		int uniqueDepthPlus1 = (unique_depth + 1);
		for (int i = 0; i < uniqueDepthPlus1; i++) {
			if (original[i + offsetDepth] != Double.MIN_VALUE) {
				original[i + uniqueDepthPlus1 + offsetDepth] = original[i + offsetDepth];
			} 
		}
		return original;
	}
	
	/**
	 * replicate inside the given integer array from [offsetDepth, offsetDepth + unique_depth + 1)
	 * to range with index starting at (offsetDepth + unique_depth + 1)
	 * 
	 * @param original integer array to replicate in place
	 * @param unique_depth replicate size = unique_depth + 1
	 * @param offsetDepth indexing support sub-range operation
	 * 
	 * @return original array after in-place replication
	 */
	private static int[] replicateInPlace(int[] original, int unique_depth, int offsetDepth) {
		int uniqueDepthPlus1 = (unique_depth + 1);
		for (int i = 0; i < uniqueDepthPlus1; i++) {
			if (original[i + offsetDepth] != Integer.MIN_VALUE) {
				original[i + uniqueDepthPlus1 + offsetDepth] = original[i + offsetDepth];
			} 
		}
		return original;
	}

	/**
	 * recursive computation of SHAP values for given tree and data tuple
	 * 
	 * @param node current tree node during tree traverse
	 * @param x data tuple
	 * @param phi shap values 
	 * @param unique_depth new depth deep down the tree
	 * @param parent_feature_indexes split feature array along the tree traverse
	 * @param parent_zero_fractions the fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param parent_one_fractions the fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param parent_pweights hold the proportion of sets of a given cardinality that are present
	 * @param parent_zero_fraction parent fraction of zero paths (where this feature is not in the set S) that flow through this branch
	 * @param parent_one_fraction parent fraction of one paths (where this feature is in the set S) that flow through this branch
	 * @param parent_feature_index parent split feature
	 * @param condition  default 0, used to divide up the condition_fraction among the recursive calls
	 * @param condition_feature used to divide up the condition_fraction among the recursive calls
	 * @param condition_fraction used to divide up the condition_fraction among the recursive calls
	 * @param totalSamples total sample number in the tree
	 * @param offsetDepth depth offset indexing into path fractions array
	 */
	private static void treeShapRecursive(Node node, 
			Tuple x, double[] phi,
			int unique_depth, 
			int[] parent_feature_indexes,
			double[] parent_zero_fractions, 
			double[] parent_one_fractions,
			double[] parent_pweights, 
			double parent_zero_fraction, double parent_one_fraction,
			int parent_feature_index, double condition, int condition_feature, double condition_fraction,
			int totalSamples, int offsetDepth) {

		// stop if we have no weight coming down to us
		if (condition_fraction == 0) {
			return;
		}
		
		// extend the unique path
		int[] feature_indexes = replicateInPlace(parent_feature_indexes, unique_depth, offsetDepth);
		double[] zero_fractions = replicateInPlace(parent_zero_fractions, unique_depth, offsetDepth);
		double[] one_fractions = replicateInPlace(parent_one_fractions, unique_depth, offsetDepth);
		double[] pweights = replicateInPlace(parent_pweights, unique_depth, offsetDepth);

		// update the depth offset indexing into path fractions
		offsetDepth += (unique_depth + 1);
		
		if (condition == 0 || condition_feature != parent_feature_index) {
			extendPath(feature_indexes, zero_fractions, one_fractions, pweights, unique_depth, parent_zero_fraction,
					parent_one_fraction, parent_feature_index, offsetDepth);
		}

		int split_index = (node instanceof InternalNode)? ((InternalNode)node).feature() : -1;
		
		// leaf node
		if (node instanceof LeafNode) {
			int loopLength = (unique_depth + 1);
			for (int i = 1; i < loopLength; i++) {
				double w = unwoundPathSum(zero_fractions, one_fractions, pweights, unique_depth, i, offsetDepth);
				double val = ((RegressionNode)node).output();
				phi[feature_indexes[i + offsetDepth]] += w * (one_fractions[i + offsetDepth] - zero_fractions[i + offsetDepth]) * val * condition_fraction;
			}
		} else {// internal node
			// find which branch is "hot" (meaning x would follow it)
			Node hot = ((InternalNode) node).trueChild();
			Node cold = ((InternalNode) node).falseChild();
			if (!((InternalNode) node).branch(x)) {
				hot = ((InternalNode) node).falseChild();
				cold = ((InternalNode) node).trueChild();
			}

			double w = (double) node.size() / (double) totalSamples;
			double hot_zero_fraction = ((double) (hot.size()) / (double) totalSamples) / w;
			double cold_zero_fraction = ((double) (cold.size()) / (double) totalSamples) / w;
			double incoming_zero_fraction = 1;
			double incoming_one_fraction = 1;

			// see if we have already split on this feature,
			// if so we undo that split so we can redo it for this node
			int path_index = 0;
			while (path_index <= unique_depth) {
				if (feature_indexes[path_index + offsetDepth] == split_index) {
					break;
				}
				path_index += 1;
			}
			if (path_index != unique_depth + 1) {
				incoming_zero_fraction = zero_fractions[path_index + offsetDepth];
				incoming_one_fraction = one_fractions[path_index + offsetDepth];
				unwindPath(feature_indexes, zero_fractions, one_fractions, pweights, unique_depth, path_index, offsetDepth);
				unique_depth -= 1;
			}

			// divide up the condition_fraction among the recursive calls
			double hot_condition_fraction = condition_fraction;
			double cold_condition_fraction = condition_fraction;
			if (condition > 0 && split_index == condition_feature) {
				cold_condition_fraction = 0;
				unique_depth -= 1;
			} else if (condition < 0 && split_index == condition_feature) {
				hot_condition_fraction *= hot_zero_fraction;
				cold_condition_fraction *= cold_zero_fraction;
				unique_depth -= 1;
			}

			treeShapRecursive(hot, x, phi, unique_depth + 1, feature_indexes,
					zero_fractions, one_fractions, pweights,
					hot_zero_fraction * incoming_zero_fraction,
					incoming_one_fraction, split_index, condition,
					condition_feature, hot_condition_fraction, totalSamples,
					offsetDepth);

			treeShapRecursive(cold, x, phi, unique_depth + 1, feature_indexes,
					zero_fractions, one_fractions, pweights,
					cold_zero_fraction * incoming_zero_fraction, 0, split_index,
					condition, condition_feature, cold_condition_fraction,
					totalSamples, offsetDepth);
		} // end else internal node
	}

}