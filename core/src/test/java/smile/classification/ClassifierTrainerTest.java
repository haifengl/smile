/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.classification;

import smile.math.matrix.DenseMatrix;
import smile.math.matrix.JMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.SparseMatrix;

import java.io.IOException;
import java.text.ParseException;

import org.junit.Assert;
import org.junit.Test;

public class ClassifierTrainerTest {

    @Test
    public void testDoubleArrayForX() throws IOException, ParseException {
        double[][] x = {{0.7220180, 0.07121225, 0.6881997}, {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}};
        int[] y = {1, 2, 3};

        DummyClassifierTrainer train = new DummyClassifierTrainer();
        train.train(x, y);
        Assert.assertEquals(1, train.count);
    }

    @Test
    public void testMatrixForX() throws IOException, ParseException {
        double[][] x = {{0.7220180, 0.07121225, 0.6881997}, {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}};
        int[] y = {1, 2, 3};

        DummyClassifierTrainer train = new DummyClassifierTrainer();
        DenseMatrix X = new JMatrix(x);
        train.train(X, y);
        train.train((Matrix) X, y);
        Assert.assertEquals(2, train.count);
    }

    @Test
    public void testSparseMatrixForX() throws IOException, ParseException {
        double[][] x = {{0.7220180, 0.07121225, 0.6881997}, {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}};
        int[] y = {1, 2, 3};

        DummyClassifierTrainer train = new DummyClassifierTrainer();
        SparseMatrix X = new SparseMatrix(x);
        train.train(X, y);
        train.train((Matrix) X, y);
        Assert.assertEquals(-2, train.count);
    }

    private static class DummyClassifierTrainer extends ClassifierTrainer<double[]> {

        int count = 0;

        @Override
        public Classifier<double[]> train(double[][] x, int[] y) {
            count++;
            return null;
        }

        @Override
        public Classifier<double[]> train(SparseMatrix x, int[] y) {
            count--;
            return null;
        }

    }

}
