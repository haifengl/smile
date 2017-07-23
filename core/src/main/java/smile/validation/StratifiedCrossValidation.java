/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.validation;

/**
 * An extension of Cross-validation for multi-class problems that adds an
 * assertion that all partition keep a similar ratio between classes as the
 * original data.
 *
 * @author Noam Segev
 */
public class StratifiedCrossValidation {

	/**
	 * The number of rounds of cross validation.
	 */
	public final int k;
	/**
	 * The index of training instances.
	 */
	public final int[][] train;
	/**
	 * The index of testing instances.
	 */
	public final int[][] test;

	/**
	 * Constructor.
	 * 
	 * @param y
	 *            the labels for the sample set.
	 * @param k
	 *            the number of rounds of cross validation.
	 */
	public StratifiedCrossValidation(int[] y, int k) {
		this(y, k, System.currentTimeMillis());
	}

	/**
	 * Constructor.
	 * 
	 * @param y
	 *            the labels for the sample set.
	 * @param k
	 *            the number of rounds of cross validation.
	 * @param seed
	 *            the initial seed for the random number generator.
	 */
	public StratifiedCrossValidation(int[] y, int k, long seed) {
		int n = y.length;
		if (n < 0) {
			throw new IllegalArgumentException("Invalid sample size: " + n);
		}

		if (k < 0 || k > n) {
			throw new IllegalArgumentException("Invalid number of CV rounds: " + k);
		}

		int[] labels = smile.math.Math.unique(y);

		this.k = k;

		int[][][] train = new int[k][labels.length][];
		int[][][] test = new int[k][labels.length][];
		for (int classLabel : labels) {
			int[] indexes = classIndexes(y, classLabel);
			n = indexes.length;
			smile.math.Random random = new smile.math.Random(seed);
			int[] index = random.permutate(n);
			int chunk = n / k;
			for (int i = 0; i < k; i++) {
				int start = chunk * i;
				int end = chunk * (i + 1);
				if (i == k - 1)
					end = n;

				train[i][classLabel] = new int[n - end + start];
				test[i][classLabel] = new int[end - start];
				for (int j = 0, p = 0, q = 0; j < n; j++) {
					if (j >= start && j < end) {
						test[i][classLabel][p++] = indexes[index[j]];
					} else {
						train[i][classLabel][q++] = indexes[index[j]];
					}
				}
			}
		}

		this.train = new int[k][];
		this.test = new int[k][];
		for (int i = 0; i < k; ++i) {
			this.test[i] = merge(test[i]);
			this.train[i] = merge(train[i]);
		}
	}

	private int[] classIndexes(int[] y, int classLabel) {
		int c = 0;
		for (int i : y) {
			if (i == classLabel) {
				++c;
			}
		}
		int[] indexes = new int[c];
		c = 0;
		for (int i = 0; i < y.length; ++i) {
			if (y[i] == classLabel) {
				indexes[c++] = i;
			}
		}
		return indexes;
	}

	private int[] merge(int[][] iss) {
		int total = 0;
		for (int[] is : iss) {
			total += is.length;
		}
		int[] ret = new int[total];
		int c = 0;
		for (int[] is : iss) {
			for (int i : is) {
				ret[c++] = i;
			};
		}
		return ret;
	}

}
