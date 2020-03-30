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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.base.cart.InternalNode;
import smile.base.cart.Node;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.regression.RegressionTree;
import smile.util.MutableDouble;
import smile.util.MutableInt;

/**
 * SHAP is a recently proposed algorithm for interpretable machine learning by
 *
 * <pre>
 * Lundberg, Scott M., and Su-In Lee. “A unified approach to interpreting model predictions.” Advances in Neural Information Processing Systems. 2017
 * </pre>
 *
 * and tree-SHAP is a specific version of implementation for tree-based machine learning like CART,
 * Random Forest, Gradient Boost, etc.
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
 * support only {@link RegressionTree} or ensemble based on it like {@link
 * smile.regression.GradientTreeBoost}
 *
 * <p>
 *
 * @author ray
 * @see
 *     <pre>
 * https://christophm.github.io/interpretable-ml-book/shap.html
 * </pre>
 */
public class EnsembleTreeSHAP {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnsembleTreeSHAP.class);
	
	/** data batch size in shap calculation concurrency */
	private static final int batchOperSize = 500;

	/** max depth among all tree in model ensemble */
	private int maxd;
	
	/** {@link TreeSHAP} structures for shap value calculation */
	private List<TreeSHAP> trees;
	
	/** indicate whether the shap importance is normalized or not */
	private boolean normalize = false;

	/** constructor with tree ensemble */
	public EnsembleTreeSHAP(RegressionTree[] trees) {
		this(trees, false);
	}

	/**
	 * constructor with tree ensemble and normalization choice (if true, shap value
	 * will be normalized)
	 */
	public EnsembleTreeSHAP(RegressionTree[] trees, boolean normalize) {
		this.normalize = normalize;
		this.trees = new ArrayList<TreeSHAP>(trees.length);

		try {
			logger.info("----- waiting for all shap trees get created -----");
			Stream.of(trees).parallel().forEach(
					tree -> {
						try {
							logger.info("start build shap tree using thread: " + Thread.currentThread().getName());
							this.trees.add(new TreeSHAP(tree, this.normalize));
						} catch (Exception e) {
							String err = "error creating shap for tree.";
							logger.error(err, e);
							throw new RuntimeException(err, e);
						}
					}
			);

			logger.info("----- all shap trees built complete for this model -----");
		} catch (Exception e) {
			String err = "error creating shap for trees:";
			logger.error(err, e);
			throw new RuntimeException(err, e);
		}

		for (TreeSHAP t : this.trees) {
			if (t.max_depth > maxd) {
				maxd = t.max_depth;
			}
		}

		// Preallocate space for the unique path data
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
		List<MutableInt> feature_indexes = initMutableList(s, false);
		List<MutableDouble> zero_fractions = initMutableList(s, true); 
		List<MutableDouble> one_fractions = initMutableList(s, true);
		List<MutableDouble> pweight = initMutableList(s, true);
		
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
	 */
	private double[] shapValues(Tuple x, boolean returnAbsolute, 
			List<MutableInt> feature_indexes,
			List<MutableDouble> zero_fractions, 
			List<MutableDouble> one_fractions,
			List<MutableDouble> pweights) {
		int length = x.schema().length();
		double[] phi = new double[length + 1];

		int treeLength = this.trees.size();
		for (int tIdx = 0; tIdx < treeLength; tIdx++) {
			try {
				treeShap(this.trees.get(tIdx), x, phi, 0.0, 0, feature_indexes, zero_fractions, one_fractions, pweights);
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
	 */
	public double[] shapImportance(StructType schema, DataFrame data) {
		double[] meanShap = new double[schema.length()];
		Arrays.setAll(meanShap, num -> 0.0);

		long startTime = System.currentTimeMillis();
		int concurrencyLevel = (data.size() / batchOperSize) + 1;		

		try {
			// avoid contending for default common forkjoin pool used in stream parallel
			ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

			logger.info("--- start calculation for data ---");
			forkJoinPool.submit(() -> 
			     IntStream.rangeClosed(1, concurrencyLevel).parallel().forEach(
				    range -> {
				       int startIdx = (range - 1) * batchOperSize;
				       int endIdx = (startIdx + batchOperSize);
				       endIdx = (endIdx > data.size() ? data.size() : endIdx);
					   logger.info("start shap value calculation for data instances at indexes:[" + startIdx + "," + endIdx + ") using thread: " 
				                   + Thread.currentThread().getName());
				       
					   double[] means = new double[data.schema().length()];
					   Arrays.setAll(means, num -> 0.0);
				       
					   int s = maxd * (maxd + 1);
					   List<MutableInt> feature_indexes = initMutableList(s, false);
					   List<MutableDouble> zero_fractions = initMutableList(s, true); 
					   List<MutableDouble> one_fractions = initMutableList(s, true);
					   List<MutableDouble> pweight = initMutableList(s, true);
					   
					   for (int xi = startIdx; xi < endIdx; xi++) {
							if (((xi - startIdx) + 1) % batchOperSize == 0) {
							    // tracking for shap value calculation progress for data tuple 
								System.out.print("*");
							}
							Tuple xx = null;
							try {
								xx = data.get(xi);
								double[] shapValues = shapValues(xx, true, feature_indexes, zero_fractions, one_fractions, pweight);
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
			logger.info(String.format("%-15s %.4f%n", schema.fieldName(i), (meanShap[i] / (double) data.size())));
		}

		return meanShap;
	}
	
	/** initialize list of {@link MutableInt} or {@link MutableDouble} */
	private List initMutableList(int size, boolean doubleValue) {
		List ret = Collections.emptyList();
		if(doubleValue) {
		   ret = new ArrayList<MutableDouble>(size);
		   for(int i = 0;i < size;i++) {
			   ret.add(new MutableDouble());
		   }
		}else {
		   ret = new ArrayList<MutableInt>(size);
		   for(int i = 0;i < size;i++) {
			   ret.add(new MutableInt(Integer.MIN_VALUE));
		   }
		}
		return ret;
	}

	private static void treeShap(TreeSHAP tree, Tuple x, double[] phi, double condition, int condition_feature, 
			List<MutableInt> feature_indexes,
			List<MutableDouble> zero_fractions, 
			List<MutableDouble> one_fractions,
			List<MutableDouble> pweights) {

		// update the bias term, which is the last index in phi
		// (note the paper has this as phi_0 instead of phi_M)
		if (condition == 0) {
			phi[phi.length - 1] += tree.values[0];
		}

		// start the recursive algorithm
		treeShapRecursive(tree.children_left, tree.children_right, tree.children_default, tree.features,
				tree.thresholds, tree.values, tree.node_sample_weight, x, phi, 0, 0, 
				feature_indexes,
				zero_fractions, 
				one_fractions, 
				pweights, 
				1, 1, -1, condition,
				condition_feature, 1);

		// reset following mutables for another tuples
		for (int i = 0; i < feature_indexes.size(); i++) {
			feature_indexes.get(i).value = Integer.MIN_VALUE;
			zero_fractions.get(i).value = Double.MIN_VALUE;
			one_fractions.get(i).value = Double.MIN_VALUE;
			pweights.get(i).value = Double.MIN_VALUE;
		}
	}

	/**
	 * extend our decision path with a fraction of one and zero extensions
	 */
	private static void extendPath(List<MutableInt> feature_indexes, 
			List<MutableDouble> zero_fractions,
			List<MutableDouble> one_fractions, 
			List<MutableDouble> pweights, 
			int unique_depth, 
			double zero_fraction,
			double one_fraction,
			int feature_index) {
		
		feature_indexes.get(unique_depth).value = feature_index;
		zero_fractions.get(unique_depth).value = zero_fraction;
		one_fractions.get(unique_depth).value = one_fraction;
		pweights.get(unique_depth).value = unique_depth == 0 ? 1.0 : 0.0;

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			 pweights.get(i + 1).value = pweights.get(i + 1).value + (one_fraction * pweights.get(i).value * (i + 1.0) / uniquePathPlus1);
			 pweights.get(i).value = zero_fraction * pweights.get(i).value * (unique_depth - i + 0.0) / uniquePathPlus1;
		}
	}

	/**
	 * undo a previous extension of the decision path
	 */
	private static void unwindPath(List<MutableInt> feature_indexes, 
			List<MutableDouble> zero_fractions,
			List<MutableDouble> one_fractions, 
			List<MutableDouble> pweights, 
			int unique_depth, int path_index) {

		double one_fraction = one_fractions.get(path_index).value;
		double zero_fraction = zero_fractions.get(path_index).value;
		double next_one_portion = pweights.get(unique_depth).value;

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			if (one_fraction != 0) {
				double tmp = pweights.get(i).value;
				pweights.get(i).value = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
				next_one_portion = tmp - pweights.get(i).value * zero_fraction * (unique_depth - i + 0.0) / uniquePathPlus1;
			} else {
				pweights.get(i).value = (pweights.get(i).value * uniquePathPlus1) / (zero_fraction * (unique_depth - i + 0.0));
			}
		}

		for (int i = path_index; i < unique_depth; i++) {
			feature_indexes.get(i).value = feature_indexes.get(i + 1).value;
			zero_fractions.get(i).value = zero_fractions.get(i + 1).value;
			one_fractions.get(i).value = one_fractions.get(i + 1).value;
		}
	}

	/**
	 * determine what the total permuation weight would be if we unwound a previous
	 * extension in the decision path
	 */
	private static double unwoundPathSum(List<MutableDouble> zero_fractions, List<MutableDouble> one_fractions,
			List<MutableDouble> pweights, int unique_depth, int path_index) {

		double one_fraction = one_fractions.get(path_index).value;
		double zero_fraction = zero_fractions.get(path_index).value;
		double next_one_portion = pweights.get(unique_depth).value;
		double total = 0;

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			if (one_fraction != 0) {
				double tmp = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
				total += tmp;
				next_one_portion = pweights.get(i).value - tmp * zero_fraction * ((unique_depth - i + 0.0) / uniquePathPlus1);
			} else {
				double numerator = pweights.get(i).value / zero_fraction;
				double denominator = (unique_depth - i + 0.0) / uniquePathPlus1;
				total += (numerator / denominator);
			}
		}

		return total;
	}	
	
	private static List shallowCopyNumbers(List original, int unique_depth, boolean doubleValue) {
		int uniqueDepthPlus1 = (unique_depth + 1);
		for (int i = 0; i < uniqueDepthPlus1; i++) {
			if (doubleValue) {
				if (original.get(i) != null && ((MutableDouble) original.get(i)).value != Double.MIN_VALUE) {
				   ((MutableDouble)original.get(i + uniqueDepthPlus1)).value = ((MutableDouble) original.get(i)).value;
				}
			} else {
				if (original.get(i) != null && ((MutableInt) original.get(i)).value != Integer.MIN_VALUE) {
				   ((MutableInt)original.get(i + uniqueDepthPlus1)).value = ((MutableInt) original.get(i)).value;
				}
			}
		}
		return original.subList(uniqueDepthPlus1, original.size());
	}

	/**
	 * recursive computation of SHAP values for a decision tree
	 */
	private static void treeShapRecursive(int[] children_left, int[] children_right, int[] children_default,
			int[] features, Node[] thresholds, double[] values, double[] node_sample_weight, Tuple x, double[] phi,
			int node_index, int unique_depth, 
			List<MutableInt> parent_feature_indexes,
			List<MutableDouble> parent_zero_fractions, 
			List<MutableDouble> parent_one_fractions,
			List<MutableDouble> parent_pweights, 
			double parent_zero_fraction, double parent_one_fraction,
			int parent_feature_index, double condition, int condition_feature, double condition_fraction) {

		// stop if we have no weight coming down to us
		if (condition_fraction == 0) {
			return;
		}
		
		// extend the unique path
		List<MutableInt> feature_indexes = shallowCopyNumbers(parent_feature_indexes, unique_depth, false);
		List<MutableDouble> zero_fractions = shallowCopyNumbers(parent_zero_fractions, unique_depth, true);
		List<MutableDouble> one_fractions = shallowCopyNumbers(parent_one_fractions, unique_depth, true);
		List<MutableDouble> pweights = shallowCopyNumbers(parent_pweights, unique_depth, true);
		
		if (condition == 0 || condition_feature != parent_feature_index) {
			extendPath(feature_indexes, zero_fractions, one_fractions, pweights, unique_depth, parent_zero_fraction,
					parent_one_fraction, parent_feature_index);
		}

		int split_index = features[node_index];

		// leaf node
		if (children_right[node_index] == -1) {
			int loopLength = (unique_depth + 1);
			for (int i = 1; i < loopLength; i++) {
				double w = unwoundPathSum(zero_fractions, one_fractions, pweights, unique_depth, i);
				phi[feature_indexes.get(i).value] += w * (one_fractions.get(i).value - zero_fractions.get(i).value) * values[node_index] * condition_fraction;
			}
		} else {// internal node
			// find which branch is "hot" (meaning x would follow it)
			int hot_index = 0;
			int cleft = children_left[node_index];
			int cright = children_right[node_index];
			if (x.isNullAt(split_index)) {
				hot_index = children_default[node_index];
			} else {
				InternalNode splitNode = (InternalNode) thresholds[node_index];
				if (splitNode.branch(x)) {
					hot_index = cleft;
				} else {
					hot_index = cright;
				}
			}

			int cold_index = hot_index == cleft ? cright : cleft;
			double w = node_sample_weight[node_index];
			double hot_zero_fraction = node_sample_weight[hot_index] / w;
			double cold_zero_fraction = node_sample_weight[cold_index] / w;
			double incoming_zero_fraction = 1;
			double incoming_one_fraction = 1;

			// see if we have already split on this feature,
			// if so we undo that split so we can redo it for this node
			int path_index = 0;
			while (path_index <= unique_depth) {
				if (feature_indexes.get(path_index).value == split_index) {
					break;
				}
				path_index += 1;
			}
			if (path_index != unique_depth + 1) {
				incoming_zero_fraction = zero_fractions.get(path_index).value;
				incoming_one_fraction = one_fractions.get(path_index).value;
				unwindPath(feature_indexes, zero_fractions, one_fractions, pweights, unique_depth, path_index);
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

			treeShapRecursive(children_left, children_right, children_default, features, thresholds, values,
					node_sample_weight, x, phi, hot_index, unique_depth + 1, feature_indexes, zero_fractions,
					one_fractions, pweights, hot_zero_fraction * incoming_zero_fraction, incoming_one_fraction,
					split_index, condition, condition_feature, hot_condition_fraction);

			treeShapRecursive(children_left, children_right, children_default, features, thresholds, values,
					node_sample_weight, x, phi, cold_index, unique_depth + 1, feature_indexes, zero_fractions,
					one_fractions, pweights, cold_zero_fraction * incoming_zero_fraction, 0, split_index, condition,
					condition_feature, cold_condition_fraction);
		} // end else internal node
	}

}