/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.manifold;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.Eurodist;

/**
 *
 * @author Haifeng Li
 */
public class MDSTest {

    public MDSTest() {
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
    public void test() {
        System.out.println("MDS");

        double[] eig = {19538377.0895, 11856555.3340};
        double[][] points = {
            { 2290.274680,  1798.80293},
            { -825.382790,   546.81148},
            {   59.183341,  -367.08135},
            {  -82.845973,  -429.91466},
            { -352.499435,  -290.90843},
            {  293.689633,  -405.31194},
            {  681.931545, -1108.64478},
            {   -9.423364,   240.40600},
            {-2048.449113,   642.45854},
            {  561.108970,  -773.36929},
            { 164.921799,   -549.36704},
            {-1935.040811,    49.12514},
            { -226.423236,   187.08779},
            {-1423.353697,   305.87513},
            { -299.498710,   388.80726},
            {  260.878046,   416.67381},
            {  587.675679,    81.18224},
            { -156.836257,  -211.13911},
            {  709.413282,  1109.36665},
            {  839.445911, -1836.79055},
            {  911.230500,   205.93020}
        };

        MDS mds = MDS.of(Eurodist.x);
        assertArrayEquals(eig, mds.scores, 1E-4);

        double sign0 = Math.signum(points[0][0] * mds.coordinates[0][0]);
        double sign1 = Math.signum(points[0][1] * mds.coordinates[0][1]);
        for (int i = 0; i < points.length; i++) {
            points[i][0] *= sign0;
            points[i][1] *= sign1;
            assertArrayEquals(points[i], mds.coordinates[i], 1E-4);
        }
    }

    @Test
    public void testPositive() {
        System.out.println("MDS positive = true");

        double[] eigs = {42274973.753, 31666186.428};
        double[][] points = {
            {-2716.561820, -3549.216493},
            { 1453.753109,  -455.895291},
            { -217.426476,  1073.442137},
            {   -1.682974,  1135.742860},
            {  461.875781,   871.913389},
            { -594.256798,  1029.818088},
            {-1271.216005,  1622.039302},
            {   88.721376,    -4.068005},
            { 3059.180990,  -836.535103},
            {-1056.316198,  1350.037932},
            { -445.663432,  1304.392098},
            { 2866.160085,  -211.043554},
            {  436.147722,   140.147975},
            { 2300.753691,  -234.863677},
            {  586.877042,  -217.428075},
            { -336.906562,  -350.948939},
            { -928.407679,   112.132182},
            {  193.653844,   847.157498},
            { -908.682100, -1742.395923},
            {-1499.140467,  1897.522969},
            {-1319.918808,  -295.010834}
        };

        MDS mds = MDS.of(Eurodist.x, 2, true);
        assertArrayEquals(eigs, mds.scores, 1E-2);

        double sign0 = Math.signum(points[0][0] * mds.coordinates[0][0]);
        double sign1 = Math.signum(points[0][1] * mds.coordinates[0][1]);
        for (int i = 0; i < points.length; i++) {
            points[i][0] *= sign0;
            points[i][1] *= sign1;
            assertArrayEquals(points[i], mds.coordinates[i], 1E-2);
        }
    }
}