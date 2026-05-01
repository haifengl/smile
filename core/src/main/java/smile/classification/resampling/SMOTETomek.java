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
package smile.classification.resampling;

import java.util.Properties;

/**
 * SMOTETomek — combined over- and under-sampling.
 * <p>
 * SMOTETomek is a two-phase hybrid resampling strategy:
 * <ol>
 *   <li><b>Over-sampling (SMOTE)</b> — synthetic minority class samples are
 *       generated using {@link SMOTE}, expanding the minority class and
 *       reducing the class imbalance ratio.</li>
 *   <li><b>Under-sampling (Tomek Links)</b> — {@link TomekLinks} cleaning is
 *       applied to the augmented dataset, removing the majority-class member
 *       of every Tomek link. This eliminates ambiguous and noisy boundary
 *       samples introduced by both the original dataset and the SMOTE
 *       synthesis step, resulting in a cleaner decision boundary.</li>
 * </ol>
 * <p>
 * The two phases are controlled independently through their respective
 * {@link SMOTE.Options} and {@link TomekLinks.Options}.
 * <p>
 * <b>Typical use</b>
 * <pre>{@code
 * var smoteOpts = new SMOTE.Options(5, 1.0);
 * var tomekOpts = new TomekLinks.Options();
 * SMOTETomek result = SMOTETomek.fit(data, labels,
 *         new SMOTETomek.Options(smoteOpts, tomekOpts));
 * }</pre>
 *
 * <h2>References</h2>
 * <ol>
 * <li>G. E. A. P. A. Batista, R. C. Prati and M. C. Monard.
 *     A study of the behavior of several methods for balancing machine
 *     learning training data. SIGKDD Explorations, 6(1):20–29, 2004.</li>
 * <li>N. V. Chawla, K. W. Bowyer, L. O. Hall and W. P. Kegelmeyer.
 *     SMOTE: Synthetic Minority Over-sampling Technique.
 *     JAIR 16:321–357, 2002.</li>
 * <li>I. Tomek. Two modifications of CNN.
 *     IEEE Transactions on Systems, Man, and Cybernetics, 6:769–772, 1976.</li>
 * </ol>
 *
 * @param data   the cleaned, balanced feature matrix.
 * @param labels the corresponding class labels.
 *
 * @author Haifeng Li
 */
public record SMOTETomek(double[][] data, int[] labels) {
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(SMOTETomek.class);

    /**
     * SMOTETomek hyperparameters.
     *
     * @param smoteOptions the {@link SMOTE} over-sampling options.
     * @param tomekOptions the {@link TomekLinks} cleaning options.
     */
    public record Options(SMOTE.Options smoteOptions, TomekLinks.Options tomekOptions) {

        /** Default constructor: default {@link SMOTE.Options} and {@link TomekLinks.Options}. */
        public Options() {
            this(new SMOTE.Options(), new TomekLinks.Options());
        }

        /** Compact constructor with null-checks. */
        public Options {
            if (smoteOptions == null) {
                throw new IllegalArgumentException("smoteOptions must not be null");
            }
            if (tomekOptions == null) {
                throw new IllegalArgumentException("tomekOptions must not be null");
            }
        }

        /**
         * Returns the persistent set of hyperparameters by merging the
         * property sets of both {@link SMOTE.Options} and
         * {@link TomekLinks.Options}.
         *
         * @return the merged persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.putAll(smoteOptions.toProperties());
            props.putAll(tomekOptions.toProperties());
            return props;
        }

        /**
         * Returns the options from a merged properties set.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            return new Options(SMOTE.Options.of(props), TomekLinks.Options.of(props));
        }
    }

    /**
     * Returns the number of samples after resampling and cleaning.
     *
     * @return the number of rows in {@code data}.
     */
    public int size() {
        return data.length;
    }

    /**
     * Applies SMOTETomek with default {@link Options}.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return a {@link SMOTETomek} record holding the balanced, cleaned dataset.
     */
    public static SMOTETomek fit(double[][] data, int[] labels) {
        return fit(data, labels, new Options());
    }

    /**
     * Applies SMOTETomek to the given dataset.
     * <p>
     * Phase 1: {@link SMOTE#fit(double[][], int[], SMOTE.Options)} generates
     * synthetic minority samples.<br>
     * Phase 2: {@link TomekLinks#fit(double[][], int[], TomekLinks.Options)}
     * removes the majority-class member of every Tomek link in the augmented
     * dataset.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @return a {@link SMOTETomek} record holding the balanced, cleaned dataset.
     * @throws IllegalArgumentException propagated from {@link SMOTE} or
     *         {@link TomekLinks} if the inputs are invalid.
     */
    public static SMOTETomek fit(double[][] data, int[] labels, Options options) {
        // Phase 1 — SMOTE over-sampling
        SMOTE smote = SMOTE.fit(data, labels, options.smoteOptions());
        logger.info("SMOTETomek: after SMOTE size={}", smote.size());

        // Phase 2 — Tomek Links cleaning
        TomekLinks tomek = TomekLinks.fit(smote.data(), smote.labels(), options.tomekOptions());
        logger.info("SMOTETomek: after TomekLinks size={}", tomek.size());

        return new SMOTETomek(tomek.data(), tomek.labels());
    }
}

