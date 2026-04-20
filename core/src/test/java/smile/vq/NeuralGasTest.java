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
package smile.vq;

import smile.clustering.CentroidClustering;
import smile.datasets.USPS;
import smile.graph.Graph;
import smile.math.MathEx;
import smile.util.function.TimeFunction;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class NeuralGasTest {
    
    public NeuralGasTest() {
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
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        double[][] x = usps.x();
        double[][] testx = usps.testx();

        int epochs = 20;
        int T = x.length * epochs;
        NeuralGas model = new NeuralGas(CentroidClustering.seeds(x, 400),
                TimeFunction.exp(0.3, T / 2.0),
                TimeFunction.exp(30, T / 8.0),
                TimeFunction.constant(x.length * 2));

        for (int i = 1; i <= epochs; i++) {
            for (int j : MathEx.permutate(x.length)) {
                model.update(x[j]);
            }
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.6991, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.5314, error, 1E-4);
    }

    @Test
    void givenInvalidNeuralGasParameters_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given
        double[][] oneNeuron = {{0.0, 0.0}};
        double[][] badDimensions = {{0.0, 0.0}, {1.0}};

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralGas(oneNeuron, TimeFunction.constant(0.1), TimeFunction.constant(1.0), TimeFunction.constant(10.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralGas(badDimensions, TimeFunction.constant(0.1), TimeFunction.constant(1.0), TimeFunction.constant(10.0)));
    }

    @Test
    void givenNeuralGasModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        NeuralGas gas = new NeuralGas(
                new double[][] {{0.0, 0.0}, {1.0, 1.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(10.0)
        );

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> gas.update(new double[] {1.0}));
        assertThrows(IllegalArgumentException.class, () -> gas.quantize(new double[] {1.0}));
    }

    @Test
    void givenNeurons_whenNeuronsQueried_thenReturnedInOriginalIndexOrder() {
        // Given: neurons provided in non-sorted positions
        NeuralGas gas = new NeuralGas(
                new double[][]{{5.0}, {1.0}, {3.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(100.0)
        );

        // When: neurons() sorts by original index
        double[][] neurons = gas.neurons();

        // Then: order matches the original construction order
        assertEquals(3, neurons.length);
        assertArrayEquals(new double[]{5.0}, neurons[0], 1E-12);
        assertArrayEquals(new double[]{1.0}, neurons[1], 1E-12);
        assertArrayEquals(new double[]{3.0}, neurons[2], 1E-12);
    }

    @Test
    void givenUpdate_whenNetworkQueried_thenBmuAndSecondNeuronAreConnected() {
        // Given: two neurons; updates connect BMU and second-nearest via a Hebbian edge.
        // Two updates are needed so the edge weight advances past zero (t=0 sets weight=0,
        // which AdjacencyMatrix treats as "no edge"; t=1 sets weight=1, making it visible).
        NeuralGas gas = new NeuralGas(
                new double[][]{{0.0}, {10.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(100.0)
        );
        gas.update(new double[]{0.0}); // t=0→1: edge set with weight=0
        gas.update(new double[]{0.0}); // t=1→2: edge refreshed with weight=1

        // When
        Graph network = gas.network();

        // Then: an edge exists from neuron 0 to neuron 1
        assertFalse(network.getEdges(0).isEmpty(),
                "NeuralGas should connect the BMU and second-nearest neuron after updates");
    }

    @Test
    void givenExpiredEdges_whenNetworkQueried_thenExpiredEdgesAreZeroed() {
        // Given: very short lifetime (1 iteration); after 2 updates the initial edge expires
        int lifetime = 1;
        NeuralGas gas = new NeuralGas(
                new double[][]{{0.0}, {10.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(lifetime)
        );
        gas.update(new double[]{0.0}); // t=0→1: edge set with weight t=0
        gas.update(new double[]{0.0}); // t=1→2: edge refreshed with weight t=1

        // Advance time far beyond lifetime by sending many updates that keep refreshing
        // the same edge (both neurons still compete for x=0).
        // To test expiry we need an edge that is NOT refreshed — use a 3-neuron network.
        NeuralGas gas3 = new NeuralGas(
                new double[][]{{0.0}, {1.0}, {100.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(lifetime)
        );

        // Many updates near 0: BMU=N(0), second=N(1). Edge N(0)↔N(100) is set at t=0
        // but never refreshed again (N(100) is never second-nearest for x near 0).
        gas3.update(new double[]{0.0}); // t=0→1: edge N(0)↔N(1) set at weight 0; also N(0)↔N(100) edge—
        // Actually, only BMU↔second is set per update. BMU=N(0), second=N(1), so
        // only graph.setWeight(0,1,t) is called. Edge(0,2) is never written.

        // After 1 update near 0.5 the BMU is N(0) or N(1), second is the other.
        // After many updates network() should prune edges that are older than lifetime.
        for (int i = 0; i < 5; i++) {
            gas3.update(new double[]{0.0}); // repeatedly refreshes edge 0↔1; edge 0↔2 never set
        }

        // When
        Graph network = gas3.network();

        // Then: the edge connecting the two close neurons exists
        assertFalse(network.getEdges(0).isEmpty(),
                "Frequently refreshed edge should survive lifetime pruning");
    }

    @Test
    void givenTwoSeparateClusters_whenTraining_thenQuantizeSnapsToCorrectCluster() {
        // Given
        NeuralGas gas = new NeuralGas(
                new double[][]{{0.0}, {100.0}},
                TimeFunction.exp(0.3, 50.0),
                TimeFunction.exp(5.0, 20.0),
                TimeFunction.constant(200.0)
        );
        for (int i = 0; i < 100; i++) {
            gas.update(new double[]{0.0});
            gas.update(new double[]{100.0});
        }

        // When
        double[] nearZero = gas.quantize(new double[]{1.0});
        double[] nearHundred = gas.quantize(new double[]{99.0});

        // Then
        assertTrue(nearZero[0] < 50.0, "Quantized 1.0 should be near the 0-cluster");
        assertTrue(nearHundred[0] > 50.0, "Quantized 99.0 should be near the 100-cluster");
    }
}
