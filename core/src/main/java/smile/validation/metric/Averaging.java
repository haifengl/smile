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
package smile.validation.metric;

/**
 * The averaging strategy to aggregate binary performance metrics across
 * multi-classes.
 *
 * @author Haifeng Li
 */
public enum Averaging {
    /**
     * Macro-averaging calculates each class's performance metric (e.g.,
     * precision, recall) and then takes the arithmetic mean across all
     * classes. So, the macro-average gives equal weight to each class,
     * regardless of the number of instances.
     */
    Macro,
    /**
     * Micro-averaging aggregates the counts of true positives, false
     * positives, and false negatives across all classes and then calculates
     * the performance metric based on the total counts. So, the micro-average
     * gives equal weight to each instance, regardless of the class label
     * and the number of samples in the class. Note that micro-average
     * precision and micro-average recall are equal to accuracy.
     */
    Micro,
    /**
     * Weighted macro for imbalanced classes. Note that weighted recall is
     * equal to accuracy.
     */
    Weighted
}
