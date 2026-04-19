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
package smile.classification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ClassifierTest {
    @Test
    public void givenEmptyModels_whenCreatingEnsemble_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, Classifier::ensemble);
    }

    @Test
    public void givenNullModel_whenCreatingEnsemble_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Classifier.ensemble(new StubClassifier(true, new int[] {0, 1}), null));
    }

    @Test
    public void givenIncompatibleClasses_whenCreatingEnsemble_thenThrowIllegalArgumentException() {
        StubClassifier c1 = new StubClassifier(true, new int[] {0, 1});
        StubClassifier c2 = new StubClassifier(true, new int[] {0, 1, 2});

        assertThrows(IllegalArgumentException.class, () -> Classifier.ensemble(c1, c2));
    }

    @Test
    public void givenHardEnsemble_whenSoftPredictionRequested_thenThrowUnsupportedOperationException() {
        Classifier<double[]> ensemble = Classifier.ensemble(new StubClassifier(false, new int[] {0, 1}), new StubClassifier(false, new int[] {0, 1}));
        assertThrows(UnsupportedOperationException.class, () -> ensemble.predict(new double[] {1.0}, new double[2]));
    }

    @Test
    public void givenInvalidPosteriorSize_whenSoftEnsemblePredict_thenThrowIllegalArgumentException() {
        Classifier<double[]> ensemble = Classifier.ensemble(new StubClassifier(true, new int[] {0, 1}), new StubClassifier(true, new int[] {0, 1}));
        assertThrows(IllegalArgumentException.class, () -> ensemble.predict(new double[] {1.0}, new double[1]));
    }

    private static class StubClassifier implements Classifier<double[]> {
        private final boolean soft;
        private final int[] classes;

        private StubClassifier(boolean soft, int[] classes) {
            this.soft = soft;
            this.classes = classes;
        }

        @Override
        public int numClasses() {
            return classes.length;
        }

        @Override
        public int[] classes() {
            return classes;
        }

        @Override
        public int predict(double[] x) {
            return classes[0];
        }

        @Override
        public boolean isSoft() {
            return soft;
        }

        @Override
        public int predict(double[] x, double[] posteriori) {
            if (!soft) {
                throw new UnsupportedOperationException("soft classification with a hard classifier");
            }

            for (int i = 0; i < posteriori.length; i++) {
                posteriori[i] = i == 0 ? 1.0 : 0.0;
            }
            return classes[0];
        }
    }
}

