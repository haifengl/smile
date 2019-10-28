/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.classification;

import smile.data.Movie;
import smile.math.MathEx;
import smile.validation.Error;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.validation.CrossValidation;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class DiscreteNaiveBayesTest {

    public DiscreteNaiveBayesTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testBatchMultinomial() {
        System.out.println("---batch Multinomial---");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, Movie.x, Movie.y, (x, y) -> {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, Movie.feature.length);
            bayes.update(x, y);
            return bayes;
        });

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(Movie.y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, Movie.x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testOnlineMultinomial() {
        System.out.println("---online Multinomial---");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, Movie.x, Movie.y, (x, y) -> {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, Movie.feature.length);
            for (int j = 0; j < x.length; j++) {
                bayes.update(x[j], y[j]);
            }
            return bayes;
        });

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(Movie.y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, Movie.x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testBatchPolyaUrn() {
        System.out.println("---batch PolyaUrn---");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, Movie.x, Movie.y, (x, y) -> {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.POLYAURN, 2, Movie.feature.length);
            bayes.update(x, y);
            return bayes;
        });

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(Movie.y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, Movie.x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testOnlinePolyaUrn() {
        System.out.println("---online PolyaUrn---");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, Movie.x, Movie.y, (x, y) -> {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.POLYAURN, 2, Movie.feature.length);
            for (int j = 0; j < x.length; j++) {
                bayes.update(x[j], y[j]);
            }
            return bayes;
        });

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(Movie.y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, Movie.x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testBatchBernoulli() {
        System.out.println("---batch Bernoulli---");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, Movie.x, Movie.y, (x, y) -> {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.BERNOULLI, 2, Movie.feature.length);
            bayes.update(x, y);
            return bayes;
        });

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(Movie.y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, Movie.x.length - nulls);
        assertEquals(309, error);
    }

    @Test
    public void testOnlineBernoulli() {
        System.out.println("---online Bernoulli---");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, Movie.x, Movie.y, (x, y) -> {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.BERNOULLI, 2, Movie.feature.length);
            for (int j = 0; j < x.length; j++) {
                bayes.update(x[j], y[j]);
            }
            return bayes;
        });

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(Movie.y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, Movie.x.length - nulls);
        assertEquals(309, error);
    }
}
