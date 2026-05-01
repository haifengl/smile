/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Resampling algorithms to balance classes.
 * In a class-imbalanced dataset, one label is considerably more common
 * than the other. The more common label is called the majority class
 * while the less common label is called the minority class.
 * <p>
 * In the real world, class-imbalanced datasets are far more common than
 * class-balanced datasets. For example, in a dataset of credit card
 * transactions, fraudulent purchases might make up less than 0.1% of
 * the examples. Similarly, in a medical diagnosis dataset, the number
 * of patients with a rare virus might be less than 0.01% of the total
 * examples.
 * <p>
 * Training machine learning models on imbalanced datasets causes significant
 * bias toward the majority class, leading to high overall accuracy but poor
 * detection of critical minority class instances (e.g., fraud or disease).
 * <p>
 * Resampling techniques address imbalanced datasets by balancing class
 * distributions. Both oversampling and undersampling involve introducing
 * a bias to select more samples from one class than from another.
 * Oversampling is generally employed more frequently than undersampling.
 *
 * @author Haifeng Li
 */
package smile.classification.resampling;
