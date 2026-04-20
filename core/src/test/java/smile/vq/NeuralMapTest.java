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

import smile.datasets.USPS;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import smile.vq.hebb.Neuron;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class NeuralMapTest {
    
    public NeuralMapTest() {
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

        NeuralMap model = new NeuralMap(x[0].length, 8, 0.01, 0.002, 50, 0.995);
        for (int i = 1; i <= 5; i++) {
            for (int j : MathEx.permutate(x.length)) {
                model.update(x[j]);
            }
            model.clear(1E-7);
            System.out.format("%d neurons after %d epochs%n", model.neurons().length, i);
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(6.0225, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.9385, error, 1E-4);
    }

    @Test
    void givenInvalidNeuralMapParameters_whenConstructingOrClearing_thenThrowsIllegalArgumentException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralMap(0, 1.0, 0.01, 0.002, 50, 0.995));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralMap(2, 1.0, 0.01, 0.002, 0, 0.995));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.0));

        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);
        assertThrows(IllegalArgumentException.class, () -> map.clear(-1E-7));
    }

    @Test
    void givenEmptyNeuralMapModel_whenQuantizing_thenThrowsIllegalStateException() {
        // Given
        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);

        // When / Then
        assertThrows(IllegalStateException.class, () -> map.quantize(new double[] {0.0, 0.0}));
    }

    @Test
    void givenNeuralMapModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> map.update(new double[] {1.0}));

        map.update(new double[] {1.0, 1.0});
        assertThrows(IllegalArgumentException.class, () -> map.quantize(new double[] {1.0}));
    }

    @Test
    void givenDistantSignal_whenUpdating_thenNewNeuronIsAdded() {
        // Given: r=0.5, so a signal farther than 0.5 from every existing neuron creates a new one
        NeuralMap map = new NeuralMap(1, 0.5, 0.1, 0.01, 50, 0.995);
        map.update(new double[]{0.0});   // bootstrap neuron 1
        map.update(new double[]{0.0});   // bootstrap neuron 2

        // When: a signal far outside the radius of any existing neuron
        map.update(new double[]{100.0}); // distance to nearest is 100 >> r=0.5 → new neuron

        // Then: the network has grown
        assertTrue(map.neurons().length >= 3,
                "A signal beyond the activation radius should create a new neuron");
    }

    @Test
    void givenTwoNearNeurons_whenSignalFallsWithinBoth_thenEdgeIsEstablished() {
        // Given: r=100 (large radius) so all bootstrap neurons are within activation range
        NeuralMap map = new NeuralMap(1, 100.0, 0.1, 0.01, 50, 0.995);
        map.update(new double[]{0.0});  // bootstrap neuron 1
        map.update(new double[]{1.0});  // bootstrap neuron 2

        // When: a signal inside both neurons' radius triggers the normal learning path
        map.update(new double[]{0.5});  // both neurons within r → Hebbian edge formed

        // Then: the two neurons are connected
        Neuron[] neurons = map.neurons();
        assertEquals(2, neurons.length);
        boolean edgeExists = neurons[0].edges.stream().anyMatch(e -> e.neighbor == neurons[1])
                || neurons[1].edges.stream().anyMatch(e -> e.neighbor == neurons[0]);
        assertTrue(edgeExists, "Neurons should be connected after a signal activates both");
    }

    @Test
    void givenBootstrapNeuronsWithZeroCounter_whenClearCalledWithPositiveEps_thenAllRemoved() {
        // Given: the first two updates hit the early-return path (size < 2 guard),
        // so those neurons are added with counter=0 and no edges.
        NeuralMap map = new NeuralMap(1, 0.5, 0.1, 0.01, 50, 0.995);
        map.update(new double[]{0.0});  // counter=0, no edges
        map.update(new double[]{10.0}); // counter=0, no edges

        // When: clear with any positive eps removes neurons with counter < eps
        map.clear(1E-10);

        // Then: both counter-zero isolated neurons are gone
        assertEquals(0, map.neurons().length,
                "Isolated neurons with zero counter should be removed by clear()");
    }

    @Test
    void givenActiveNeuron_whenClearCalledWithZeroEps_thenNoNeuronsRemovedByCounter() {
        // Given: neurons that participate in learning have counter > 0
        NeuralMap map = new NeuralMap(1, 100.0, 1.0, 0.1, 50, 0.995);
        map.update(new double[]{0.0});
        map.update(new double[]{1.0});
        // Normal update: s1.counter += 1
        map.update(new double[]{0.5});

        int before = map.neurons().length;

        // When: clear with eps=0.0 never removes neurons on the counter criterion alone
        map.clear(0.0);

        // Then: connected neurons are retained
        assertTrue(map.neurons().length > 0,
                "Connected neurons should not be removed when eps=0.0");
        assertTrue(map.neurons().length <= before,
                "clear() should not add neurons");
    }

    @Test
    void givenNeuralMap_whenQuantizingAfterTraining_thenSnapsToNearestPrototype() {
        // Given: two clusters far apart; large r so learning is active
        NeuralMap map = new NeuralMap(1, 100.0, 0.5, 0.05, 50, 0.995);
        for (int i = 0; i < 10; i++) {
            map.update(new double[]{0.0});
            map.update(new double[]{100.0});
        }

        // When
        double[] nearZero = map.quantize(new double[]{1.0});
        double[] nearHundred = map.quantize(new double[]{99.0});

        // Then
        assertTrue(nearZero[0] < 50.0, "Quantized 1.0 should be near the 0-cluster");
        assertTrue(nearHundred[0] > 50.0, "Quantized 99.0 should be near the 100-cluster");
    }
}
