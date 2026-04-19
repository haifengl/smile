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
package smile.manifold;

import java.util.Properties;
import smile.datasets.MNIST;
import smile.math.MathEx;
import smile.feature.extraction.PCA;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TSNETest {

    public TSNETest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    @Tag("integration")
    public void test() throws Exception {
        System.out.println("tSNE");
        var mnist = new MNIST();
        double[][] x = mnist.x();
        PCA pca = PCA.fit(x).getProjection(50);
        double[][] X = pca.apply(x);

        long start = System.currentTimeMillis();
        TSNE tsne = TSNE.fit(X, new TSNE.Options(2, 20, 200, 12, 550));
        long end = System.currentTimeMillis();
        System.out.format("t-SNE takes %.2f seconds\n", (end - start) / 1000.0);

        assertEquals(1.4170, tsne.cost(), 1E-3);
    }

    @Test
    public void givenOptions_whenRoundTripToProperties_thenValuesPreserved() {
        // Given
        TSNE.Options options = new TSNE.Options(2, 25, 150, 10, 500);

        // When
        Properties props = options.toProperties();
        TSNE.Options restored = TSNE.Options.of(props);

        // Then
        assertEquals(options.d(), restored.d());
        assertEquals(options.perplexity(), restored.perplexity(), 1E-12);
        assertEquals(options.eta(), restored.eta(), 1E-12);
        assertEquals(options.earlyExaggeration(), restored.earlyExaggeration(), 1E-12);
        assertEquals(options.maxIter(), restored.maxIter());
        assertEquals(options.maxIterWithoutProgress(), restored.maxIterWithoutProgress());
        assertEquals(options.tol(), restored.tol(), 1E-12);
        assertEquals(options.momentum(), restored.momentum(), 1E-12);
        assertEquals(options.finalMomentum(), restored.finalMomentum(), 1E-12);
        assertEquals(options.momentumSwitchIter(), restored.momentumSwitchIter());
        assertEquals(options.minGain(), restored.minGain(), 1E-12);
    }

    @Test
    public void givenSparseProperties_whenParsingOptions_thenDefaultsApplied() {
        // Given
        Properties props = new Properties();
        props.setProperty("smile.t_sne.early_exaggeration", "12");
        props.setProperty("smile.t_sne.momentum", "0.5");
        props.setProperty("smile.t_sne.final_momentum", "0.8");
        props.setProperty("smile.t_sne.momentum_switch", "250");
        props.setProperty("smile.t_sne.min_gain", "0.01");

        // When
        TSNE.Options options = TSNE.Options.of(props);

        // Then
        assertEquals(2, options.d());
        assertEquals(20.0, options.perplexity(), 1E-12);
        assertEquals(200.0, options.eta(), 1E-12);
        assertEquals(12.0, options.earlyExaggeration(), 1E-12);
        assertEquals(1000, options.maxIter());
        assertEquals(0.01, options.minGain(), 1E-12);
    }
}
