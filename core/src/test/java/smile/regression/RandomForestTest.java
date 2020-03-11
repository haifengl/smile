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

package smile.regression;

import java.util.Arrays;
import org.junit.*;
import smile.data.*;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.Validation;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class RandomForestTest {
    long[] seeds = {
            342317953, 521642753, 72070657, 577451521, 266953217, 179976193,
            374603777, 527788033, 303395329, 185759582, 261518209, 461300737,
            483646580, 532528741, 159827201, 284796929, 655932697, 26390017,
            454330473, 867526205, 824623361, 719082324, 334008833, 699933293,
            823964929, 155216641, 150210071, 249486337, 713508520, 558398977,
            886227770, 74062428, 670528514, 701250241, 363339915, 319216345,
            757017601, 459643789, 170213767, 434634241, 414707201, 153100613,
            753882113, 546490145, 412517763, 888761089, 628632833, 565587585,
            175885057, 594903553, 78450978, 212995578, 710952449, 835852289,
            415422977, 832538705, 624345857, 839826433, 260963602, 386066438,
            530942946, 261866663, 269735895, 798436064, 379576194, 251582977,
            349161809, 179653121, 218870401, 415292417, 86861523, 570214657,
            701581299, 805955890, 358025785, 231452966, 584239408, 297276298,
            371814913, 159451160, 284126095, 896291329, 496278529, 556314113,
            31607297, 726761729, 217004033, 390410146, 70173193, 661580775,
            633589889, 389049037, 112099159, 54041089, 80388281, 492196097,
            912179201, 699398161, 482080769, 363844609, 286008078, 398098433,
            339855361, 189583553, 697670495, 709568513, 98494337, 99107427,
            433350529, 266601473, 888120086, 243906049, 414781441, 154685953,
            601194298, 292273153, 212413697, 568007473, 666386113, 712261633,
            802026964, 783034790, 188095005, 742646355, 550352897, 209421313,
            175672961, 242531185, 157584001, 201363231, 760741889, 852924929,
            60158977, 774572033, 311159809, 407214966, 804474160, 304456514,
            54251009, 504009638, 902115329, 870383757, 487243777, 635554282,
            564918017, 636074753, 870308031, 817515521, 494471884, 562424321,
            81710593, 476321537, 595107841, 418699893, 315560449, 773617153,
            163266399, 274201241, 290857537, 879955457, 801949697, 669025793,
            753107969, 424060977, 661877468, 433391617, 222716929, 334154852,
            878528257, 253742849, 480885528, 99773953, 913761493, 700407809,
            483418083, 487870398, 58433153, 608046337, 475342337, 506376199,
            378726401, 306604033, 724646374, 895195218, 523634541, 766543466,
            190068097, 718704641, 254519245, 393943681, 796689751, 379497473,
            50014340, 489234689, 129556481, 178766593, 142540536, 213594113,
            870440184, 277912577};

    public RandomForestTest() {
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

    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        MathEx.setSeed(19650218); // to get repeatable results for cross validation.
        RandomForest model = RandomForest.fit(Longley.formula, Longley.data, 100, 3, 20, 10, 3, 1.0, Arrays.stream(seeds));

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        assertEquals(39293.8193, importance[0], 1E-4);
        assertEquals(6595.3241,  importance[1], 1E-4);
        assertEquals(10242.6828, importance[2], 1E-4);
        assertEquals(34755.6754, importance[3], 1E-4);
        assertEquals(31075.0867, importance[4], 1E-4);
        assertEquals(31644.9317, importance[5], 1E-4);

        System.out.println("----- Progressive RMSE -----");
        double[][] test = model.test(Longley.data);
        for (int i = 0; i < test.length; i++) {
            System.out.format("RMSE with %3d trees: %.4f%n", i+1, RMSE.of(Longley.y, test[i]));
        }

        double[] prediction = LOOCV.regression(Longley.formula, Longley.data, (f, x) -> RandomForest.fit(f, x, 100, 3, 20, 10, 3, 1.0, Arrays.stream(seeds)));
        double rmse = RMSE.of(Longley.y, prediction);

        System.out.println("LOOCV RMSE = " + rmse);
        assertEquals(2.710121445970332, rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    public void test(String name, Formula formula, DataFrame data, double expected) {
        System.out.println(name);

        MathEx.setSeed(19650218); // to get repeatable results for cross validation.
        double[] prediction = CrossValidation.regression(3, formula, data, (f, x) -> RandomForest.fit(f, x, 100, 3, 20, 100, 5, 1.0, Arrays.stream(seeds)));
        double rmse = RMSE.of(formula.y(data).toDoubleArray(), prediction);
        System.out.format("10-CV RMSE = %.4f%n", rmse);
        assertEquals(expected, rmse, 1E-4);

        RandomForest model = RandomForest.fit(formula, data);
        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }
    }

    @Test
    public void testAll() {
        test("CPU", CPU.formula, CPU.data, 75.0190);
        test("2dplanes", Planes.formula, Planes.data, 1.3574);
        test("abalone", Abalone.formula, Abalone.train, 2.1911);
        test("ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test("bank32nh", Bank32nh.formula, Bank32nh.data, 0.0978);
        test("autoMPG", AutoMPG.formula, AutoMPG.data, 2.8868);
        test("cal_housing", CalHousing.formula, CalHousing.data, 58552.3225);
        test("puma8nh", Puma8NH.formula, Puma8NH.data, 3.3149);
        test("kin8nm", Kin8nm.formula, Kin8nm.data, 0.1704);
    }

    @Test
    public void testRandomForestMerging() throws Exception {
        System.out.println("Random forest merging");

        RandomForest forest1 = RandomForest.fit(Abalone.formula, Abalone.train, 50, 3, 20, 100, 5, 1.0, Arrays.stream(seeds));
        RandomForest forest2 = RandomForest.fit(Abalone.formula, Abalone.train, 50, 3, 20, 100, 5, 1.0, Arrays.stream(seeds).skip(50));
        RandomForest forest = forest1.merge(forest2);
        double rmse1 = RMSE.of(Abalone.testy, Validation.test(forest1, Abalone.test));
        double rmse2 = RMSE.of(Abalone.testy, Validation.test(forest2, Abalone.test));
        double rmse  = RMSE.of(Abalone.testy, Validation.test(forest,  Abalone.test));
        System.out.format("Forest 1 RMSE = %.4f%n", rmse1);
        System.out.format("Forest 2 RMSE = %.4f%n", rmse2);
        System.out.format("Merged   RMSE = %.4f%n", rmse);
        assertEquals(2.0858, rmse1, 1E-4);
        assertEquals(2.0630, rmse2, 1E-4);
        assertEquals(2.0691, rmse,  1E-4);
    }
}
