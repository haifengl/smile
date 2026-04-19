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
package smile.model;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted robustness tests for {@link Model} wrappers.
 */
public class ModelRobustnessTest {
    @Test
    public void testGivenClassificationWrapperWhenInspectingSchemaAndTagsThenResponseIsExcludedAndTagsAreCloned() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {0.0},
                {1.0},
                {2.0},
                {3.0},
                {4.0},
                {5.0}
        }, "x").add(new IntVector("y", new int[] {0, 0, 0, 1, 1, 1}));
        Properties params = new Properties();
        params.setProperty("custom.tag", "original");

        // When
        ClassificationModel model = Model.classification("logistic", Formula.lhs("y"), data, null, params);
        params.setProperty("custom.tag", "mutated");
        model.setProperty(Model.ID, "classification-model");

        // Then
        assertEquals("logistic", model.algorithm());
        assertArrayEquals(new String[] {"x"}, model.schema().names());
        assertEquals("original", model.getTag("custom.tag"));
        assertEquals("classification-model", model.getTag(Model.ID));
        assertEquals("fallback", model.getTag("missing", "fallback"));
        assertEquals(2, model.numClasses());
    }

    @Test
    public void testGivenRegressionWrapperWhenInspectingSchemaAndTagsThenResponseIsExcludedAndTagsAreCloned() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {0.0},
                {1.0},
                {2.0},
                {3.0}
        }, "x").add(new DoubleVector("y", new double[] {1.0, 3.0, 5.0, 7.0}));
        Properties params = new Properties();
        params.setProperty("custom.tag", "original");

        // When
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), data, null, params);
        params.setProperty("custom.tag", "mutated");
        model.setProperty(Model.VERSION, "1.0.0");

        // Then
        assertEquals("ols", model.algorithm());
        assertArrayEquals(new String[] {"x"}, model.schema().names());
        assertEquals("original", model.getTag("custom.tag"));
        assertEquals("1.0.0", model.getTag(Model.VERSION));
        assertEquals("fallback", model.getTag("missing", "fallback"));
        assertEquals(7.0, model.predict(data.get(3)), 1E-10);
    }

    @Test
    public void testGivenUnsupportedClassificationAlgorithmWhenTrainingThenThrowsMeaningfulException() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {0.0},
                {1.0}
        }, "x").add(new IntVector("y", new int[] {0, 1}));

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Model.classification("unsupported", Formula.lhs("y"), data, null, new Properties()));

        // Then
        assertTrue(exception.getMessage().contains("Unsupported algorithm"));
    }

    @Test
    public void testGivenUnsupportedRegressionAlgorithmWhenTrainingThenThrowsMeaningfulException() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {0.0},
                {1.0}
        }, "x").add(new DoubleVector("y", new double[] {0.0, 1.0}));

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Model.regression("unsupported", Formula.lhs("y"), data, null, new Properties()));

        // Then
        assertTrue(exception.getMessage().contains("Unsupported algorithm"));
    }
}


