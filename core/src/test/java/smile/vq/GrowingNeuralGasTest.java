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
public class GrowingNeuralGasTest {
    
    public GrowingNeuralGasTest() {
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

        GrowingNeuralGas model = new GrowingNeuralGas(x[0].length);
        for (int i = 1; i <= 10; i++) {
            for (int j : MathEx.permutate(x.length)) {
                model.update(x[j]);
            }
            System.out.format("%d neurons after %d epochs%n", model.neurons().length, i);
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.5931, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.4317, error, 1E-4);
    }

    @Test
    void givenInvalidGrowingNeuralGasParameters_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new GrowingNeuralGas(0));
        assertThrows(IllegalArgumentException.class,
                () -> new GrowingNeuralGas(2, 0.2, 0.006, 0, 100, 0.5, 0.995));
        assertThrows(IllegalArgumentException.class,
                () -> new GrowingNeuralGas(2, 0.2, 0.006, 50, 0, 0.5, 0.995));
    }

    @Test
    void givenEmptyGrowingNeuralGasModel_whenQuantizing_thenThrowsIllegalStateException() {
        // Given
        GrowingNeuralGas gng = new GrowingNeuralGas(2);

        // When / Then
        assertThrows(IllegalStateException.class, () -> gng.quantize(new double[] {0.0, 0.0}));
    }

    @Test
    void givenGrowingNeuralGasModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        GrowingNeuralGas gng = new GrowingNeuralGas(2);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> gng.update(new double[] {1.0}));

        gng.update(new double[] {0.0, 0.0});
        gng.update(new double[] {1.0, 1.0});
        assertThrows(IllegalArgumentException.class, () -> gng.quantize(new double[] {1.0}));
    }

    @Test
    void givenTwoBootstrapNeurons_afterFirstNormalUpdate_thenEdgeIsEstablished() {
        // Given: bootstrap two neurons then trigger one normal update
        GrowingNeuralGas gng = new GrowingNeuralGas(1);
        gng.update(new double[]{0.0});  // t=1: bootstrap neuron at 0
        gng.update(new double[]{1.0});  // t=2: bootstrap neuron at 1

        // When: a signal triggers the normal learning path (connects BMU to second-nearest)
        gng.update(new double[]{0.0});  // t=3: connects N(0) and N(1) via a Hebbian edge

        // Then: both neurons are topologically connected
        Neuron[] neurons = gng.neurons();
        assertEquals(2, neurons.length);
        boolean edgeExists = neurons[0].edges.stream().anyMatch(e -> e.neighbor == neurons[1])
                || neurons[1].edges.stream().anyMatch(e -> e.neighbor == neurons[0]);
        assertTrue(edgeExists, "Neurons should be connected after the first non-bootstrap update");
    }

    @Test
    void givenNetworkWithEdges_whenLambdaSignalsProcessed_thenNewNeuronInserted() {
        // Given: lambda=5 so a neuron is inserted after every 5 signals
        GrowingNeuralGas gng = new GrowingNeuralGas(1, 0.2, 0.006, 50, 5, 0.5, 0.995);
        gng.update(new double[]{0.0}); // t=1: bootstrap
        gng.update(new double[]{1.0}); // t=2: bootstrap

        // t=3,4: establish and refresh the edge between N(0) and N(1)
        gng.update(new double[]{0.0}); // t=3
        gng.update(new double[]{1.0}); // t=4

        // When: t=5 is a multiple of lambda → neuron insertion is triggered
        gng.update(new double[]{0.0}); // t=5

        // Then: the network has grown beyond the initial 2 neurons
        assertTrue(gng.neurons().length > 2,
                "A new neuron should be inserted when t is a multiple of lambda");
    }

    @Test
    void givenNetworkAfterInsertion_whenQuantizing_thenReturnsClosestPrototype() {
        // Given: train on two clearly separated clusters
        GrowingNeuralGas gng = new GrowingNeuralGas(1, 0.5, 0.01, 50, 5, 0.5, 0.995);
        gng.update(new double[]{0.0});
        gng.update(new double[]{100.0});
        for (int i = 0; i < 20; i++) {
            gng.update(new double[]{0.0});
            gng.update(new double[]{100.0});
        }

        // When
        double[] nearZero = gng.quantize(new double[]{1.0});
        double[] nearHundred = gng.quantize(new double[]{99.0});

        // Then: quantize snaps to the nearest prototype
        assertTrue(nearZero[0] < 50.0, "Quantized 1.0 should be close to the 0-cluster");
        assertTrue(nearHundred[0] > 50.0, "Quantized 99.0 should be close to the 100-cluster");
    }

    @Test
    void givenAggressiveEdgePruning_whenManyUpdates_thenNeverThrows() {
        // Given: edgeLifetime=1 causes frequent edge pruning; lambda=5 causes frequent
        // insertion attempts. Without the guard on q.edges.isEmpty(), the insertion
        // would crash with NoSuchElementException whenever the max-error neuron's
        // edges have all been pruned before the lambda trigger fires.
        GrowingNeuralGas gng = new GrowingNeuralGas(1, 0.2, 0.006, 1, 5, 0.5, 0.995);

        // When / Then: 60 updates across two well-separated clusters should not throw
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 60; i++) {
                gng.update(new double[]{i % 2 == 0 ? 0.0 : 100.0});
            }
        });
    }
}
