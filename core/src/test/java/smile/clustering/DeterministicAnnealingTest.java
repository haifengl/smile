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

package smile.clustering;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.util.Paths;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class DeterministicAnnealingTest {
    
    public DeterministicAnnealingTest() {
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

    /**
     * Test of learn method, of class DeterministicAnnealing.
     */
    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        ArrayList<StructField> fields = new ArrayList<>();
        fields.add(new StructField("class", DataTypes.ByteType));
        IntStream.range(0, 256).forEach(i -> fields.add(new StructField("V"+i, DataTypes.ByteType)));
        StructType schema = DataTypes.struct(fields);

        CSV csv = new CSV(CSVFormat.DEFAULT.withDelimiter(' '));
        csv.schema(schema);

        DataFrame train = csv.read(Paths.getTestData("usps/zip.train"));
        DataFrame test = csv.read(Paths.getTestData("usps/zip.test"));
        Formula formula = Formula.lhs("class");

        double[][] x = formula.frame(train).toArray();
        int[] y = formula.response(train).toIntArray();
        double[][] testx = formula.frame(test).toArray();
        int[] testy = formula.response(test).toIntArray();


        DeterministicAnnealing annealing = new DeterministicAnnealing(x, 10, 0.8);
            
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(y, annealing.getClusterLabel());
        double r2 = ari.measure(y, annealing.getClusterLabel());
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.75);
        assertTrue(r2 > 0.25);
            
        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = annealing.predict(testx[i]);
        }
            
        r = rand.measure(testy, p);
        r2 = ari.measure(testy, p);
        System.out.format("Testing rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.75);
        assertTrue(r2 > 0.3);
    }
}
