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
package smile.anomaly;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import smile.io.Read;
import smile.io.Write;
import smile.math.kernel.GaussianKernel;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SVMTest {

    @Test
    public void givenSyntheticData_whenScoring_thenOutlierScoreLowerThanInlier() {
        // Given
        double[][] data = {
                {0.00, 0.00}, {0.05, 0.02}, {-0.04, 0.03}, {0.03, -0.05},
                {-0.02, -0.01}, {0.06, -0.02}, {-0.03, 0.00}, {0.01, 0.04}
        };

        SVM<double[]> model = SVM.fit(data, new GaussianKernel(1.0), new SVM.Options(0.2, 1E-3));

        // When
        double inlier = model.score(new double[] {0.02, 0.01});
        double outlier = model.score(new double[] {4.0, -4.0});

        // Then
        assertTrue(Double.isFinite(inlier));
        assertTrue(Double.isFinite(outlier));
        assertTrue(outlier < inlier);
    }

    @Test
    public void givenModel_whenSerializing_thenRoundTripIsReadable() throws Exception {
        // Given
        double[][] data = {
                {0.0, 0.0}, {0.1, 0.0}, {0.0, 0.1}, {-0.1, 0.0}, {0.0, -0.1}
        };
        SVM<double[]> model = SVM.fit(data, new GaussianKernel(1.0));

        // When
        Path temp = Write.object(model);
        Object restored = Read.object(temp);

        // Then
        assertNotNull(restored);
        assertTrue(restored instanceof SVM<?>);
    }

    @Test
    public void givenOptions_whenRoundTripToProperties_thenValuesPreserved() {
        // Given
        SVM.Options options = new SVM.Options(0.3, 1E-4);

        // When
        Properties props = options.toProperties();
        SVM.Options restored = SVM.Options.of(props);

        // Then
        assertEquals(options, restored);
    }

    @Test
    public void givenInvalidInputs_whenFitting_thenThrowsIllegalArgumentException() {
        // Given
        GaussianKernel kernel = new GaussianKernel(1.0);
        double[][] x = {{0.0, 0.0}, {1.0, 1.0}};

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> SVM.fit(null, kernel));
        assertThrows(IllegalArgumentException.class, () -> SVM.fit(new double[0][], kernel));
        assertThrows(IllegalArgumentException.class, () -> SVM.fit(x, null));
        assertThrows(IllegalArgumentException.class, () -> SVM.fit(x, kernel, null));
    }
}
