/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

/**
 * Anomaly detection is the identification of rare items, events
 * or observations which raise suspicions by differing significantly from
 * the majority of the data. Anomalies are also referred to as outliers,
 * novelties, noise, deviations and exceptions.
 * <p>
 * Three broad categories of anomaly detection techniques exist.
 * Unsupervised anomaly detection techniques detect anomalies in an unlabeled
 * test data set under the assumption that the majority of the instances
 * in the data set are normal by looking for instances that seem to fit
 * least to the remainder of the data set. Supervised anomaly detection
 * techniques require a data set that has been labeled as "normal" and
 * "abnormal" and involves training a classifier (the key difference to
 * many other statistical classification problems is the inherent unbalanced
 * nature of outlier detection). Semi-supervised anomaly detection techniques
 * construct a model representing normal behavior from a given normal training
 * data set, and then test the likelihood of a test instance to be generated
 * by the utilized model.
 * <p>
 * In particular, in the context of abuse and network intrusion detection,
 * the interesting objects are often not rare objects, but unexpected
 * bursts in activity. This pattern does not adhere to the common
 * statistical definition of an outlier as a rare object, and many outlier
 * detection methods (in particular unsupervised methods) will fail on
 * such data, unless it has been aggregated appropriately. Instead, a cluster
 * analysis algorithm may be able to detect the micro clusters formed by
 * these patterns.
 *
 * @author Haifeng Li
 */
package smile.anomaly;