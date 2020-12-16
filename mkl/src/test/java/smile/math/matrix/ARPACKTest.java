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

package smile.math.matrix;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static smile.math.blas.UPLO.*;

/**
 *
 * @author Haifeng Li
 */
public class ARPACKTest {
    double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
    };
    double[][] eigenVectors = {
            {0.6881997, -0.07121225,  0.7220180},
            {0.3700456,  0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
    };
    double[] eigenValues = {1.7498382, 0.3165784, 0.1335834};

    public ARPACKTest() {
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
    public void testSA() {
        System.out.println("SA");
        Matrix a = new Matrix(A);
        a.uplo(LOWER);
        Matrix.EVD eig = ARPACK.syev(a, ARPACK.SymmOption.SA, 2);
        assertEquals(eigenValues[1], eig.wr[0], 1E-4);
        assertEquals(eigenValues[2], eig.wr[1], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][1]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(eig.Vr.get(i, 1)), 1E-4);
        }

        // non-symmetric
        eig = ARPACK.eigen(a, ARPACK.AsymmOption.SM, 1);
        assertEquals(eigenValues[2], eig.wr[0], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testLA() {
        System.out.println("LA");
        Matrix a = new Matrix(A);
        a.uplo(LOWER);
        Matrix.EVD eig = ARPACK.syev(a, ARPACK.SymmOption.LA, 1);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        // non-symmetric
        eig = ARPACK.eigen(a, ARPACK.AsymmOption.LM, 1);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testSymmSA() {
        System.out.println("Symm SA");
        SymmMatrix a = new SymmMatrix(LOWER, A);
        Matrix.EVD eig = ARPACK.syev(a, ARPACK.SymmOption.SA, 2);
        assertEquals(eigenValues[1], eig.wr[0], 1E-4);
        assertEquals(eigenValues[2], eig.wr[1], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][1]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(eig.Vr.get(i, 1)), 1E-4);
        }

        // non-symmetric
        eig = ARPACK.eigen(a, ARPACK.AsymmOption.SM, 1);
        assertEquals(eigenValues[2], eig.wr[0], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testSymmLA() {
        System.out.println("SymmLA");
        SymmMatrix a = new SymmMatrix(LOWER, A);
        Matrix.EVD eig = ARPACK.syev(a, ARPACK.SymmOption.LA, 1);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        // non-symmetric
        eig = ARPACK.eigen(a, ARPACK.AsymmOption.LM, 1);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testDiag500() {
        System.out.println("500 x 500 diagonal matrix");
        BandMatrix a = new BandMatrix(500, 500, 0, 0);
        a.set(0, 0, 2.0);
        a.set(1, 1, 2.0);
        a.set(2, 2, 2.0);
        a.set(3, 3, 2.0);
        for (int i = 4; i < 500; i++) {
            a.set(i, i, (500 - i) / 500.0);
        }

        Matrix.EVD eig = ARPACK.syev(a, ARPACK.SymmOption.LA, 6);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);

        // non-symmetric
        eig = ARPACK.eigen(a, ARPACK.AsymmOption.LM, 6);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }
    }

    @Test
    public void testSVD1() {
        System.out.println("svd 3x3");
        double[][] A = {
                {0.9000, 0.4000, 0.7000},
                {0.4000, 0.5000, 0.3000},
                {0.7000, 0.3000, 0.8000}
        };

        double[] s = {1.7498382, 0.3165784, 0.1335834};

        double[][] U = {
                {0.6881997, -0.07121225, 0.7220180},
                {0.3700456, 0.89044952, -0.2648886},
                {0.6240573, -0.44947578, -0.6391588}
        };

        double[][] V = {
                {0.6881997, -0.07121225, 0.7220180},
                {0.3700456, 0.89044952, -0.2648886},
                {0.6240573, -0.44947578, -0.6391588}
        };

        int m = A.length;
        int n = A[0].length;
        int k = 1;

        Matrix.SVD svd = ARPACK.svd(new Matrix(A), k);
        for (int i = 0; i < k; i++) {
            assertEquals(s[i], svd.s[i], 1E-6);
        }

        assertEquals(m, svd.U.nrow());
        assertEquals(k, svd.U.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(n, svd.V.nrow());
        assertEquals(k, svd.V.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }

    @Test
    public void testSVD2() {
        System.out.println("svd 7x7");
        double[][] A = {
                {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882, -0.04247433},
                {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340},
                {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532, 0.17966051},
                {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350, -1.19575563},
                {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502, -1.88207227},
                {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873, -0.5553013, 1.63805985},
                {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535, -0.2158704, -0.27529472}
        };

        double[] s = {3.8589375, 3.4396766, 2.6487176, 2.2317399, 1.5165054, 0.8109055, 0.2706515};

        double[][] U = {
                {-0.3082776, 0.77676231, 0.01330514, 0.23231424, -0.47682758, 0.13927109, 0.02640713},
                {-0.4013477, -0.09112050, 0.48754440, 0.47371793, 0.40636608, 0.24600706, -0.37796295},
                {0.0599719, -0.31406586, 0.45428229, -0.08071283, -0.38432597, 0.57320261, 0.45673993},
                {-0.7694214, -0.12681435, -0.05536793, -0.62189972, -0.02075522, -0.01724911, -0.03681864},
                {-0.3319069, -0.17984404, -0.54466777, 0.45335157, 0.19377726, 0.12333423, 0.55003852},
                {0.1259351, 0.49087824, 0.16349687, -0.32080176, 0.64828744, 0.20643772, 0.38812467},
                {0.1491884, 0.01768604, -0.47884363, -0.14108924, 0.03922507, 0.73034065, -0.43965505}
        };

        double[][] V = {
                {-0.2122609, -0.54650056, 0.08071332, -0.43239135, -0.2925067, 0.1414550, 0.59769207},
                {-0.1943605, 0.63132116, -0.54059857, -0.37089970, -0.1363031, 0.2892641, 0.17774114},
                {0.3031265, -0.06182488, 0.18579097, -0.38606409, -0.5364911, 0.2983466, -0.58642548},
                {0.1844063, 0.24425278, 0.25923756, 0.59043765, -0.4435443, 0.3959057, 0.37019098},
                {-0.7164205, 0.30694911, 0.58264743, -0.07458095, -0.1142140, -0.1311972, -0.13124764},
                {-0.1103067, -0.10633600, 0.18257905, -0.03638501, 0.5722925, 0.7784398, -0.09153611},
                {-0.5156083, -0.36573746, -0.47613340, 0.41342817, -0.2659765, 0.1654796, -0.32346758}
        };

        int m = A.length;
        int n = A[0].length;
        int k = 3;

        Matrix.SVD svd = ARPACK.svd(new Matrix(A), k);
        for (int i = 0; i < k; i++) {
            assertEquals(s[i], svd.s[i], 1E-6);
        }

        assertEquals(m, svd.U.nrow());
        assertEquals(k, svd.U.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(n, svd.V.nrow());
        assertEquals(k, svd.V.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }

    @Test
    public void testSVD3() {
        System.out.println("svd m = n+1");
        double[][] A = {
                {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882},
                {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110},
                {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532},
                {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350},
                {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502},
                {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873, -0.5553013},
                {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535, -0.2158704}
        };

        double[] s = {3.6447007, 3.1719019, 2.4155022, 1.6952749, 1.0349052, 0.6735233};

        double[][] U = {
                {-0.66231606, 0.51980064, -0.26908096, -0.33255132, 0.1998343961, 0.25344461},
                {-0.30950323, -0.38356363, -0.57342388, 0.43584295, -0.2842953084, 0.06773874},
                {0.17209598, -0.40152786, -0.25549740, -0.47194228, -0.1795895194, 0.60960160},
                {-0.58855512, -0.52801793, 0.59486615, -0.13721651, -0.0004042427, -0.01414006},
                {-0.06838272, 0.03221968, 0.14785619, 0.64819245, 0.3955572924, 0.53374206},
                {-0.23683786, 0.25613876, 0.07459517, 0.19208798, -0.7235935956, -0.10586201},
                {0.16959559, 0.27570548, 0.39014092, 0.02900709, -0.4085787191, 0.51310416}
        };

        double[][] V = {
                {-0.08624942, 0.642381656, -0.35639657, 0.2600624, -0.303192728, -0.5415995},
                {0.46728106, -0.567452824, -0.56054543, 0.1717478, 0.067268188, -0.3337846},
                {-0.26399674, -0.005897261, -0.02438536, 0.8302504, 0.448103782, 0.1989057},
                {-0.03389306, -0.296652409, 0.68563317, 0.2309273, -0.145824242, -0.6051146},
                {0.83642784, 0.352498963, 0.29305340, 0.2264531, 0.006202435, 0.1973149},
                {0.06127719, 0.230326187, 0.04693098, -0.3300697, 0.825499232, -0.3880689}
        };

        int m = A.length;
        int n = A[0].length;
        int k = 3;

        Matrix.SVD svd = ARPACK.svd(new Matrix(A),k);
        for (int i = 0; i < k; i++) {
            assertEquals(s[i], svd.s[i], 1E-6);
        }

        assertEquals(m, svd.U.nrow());
        assertEquals(k, svd.U.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(n, svd.V.nrow());
        assertEquals(k, svd.V.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }

    @Test
    public void testSVD4() {
        System.out.println("svd m = n+2");
        double[][] A = {
                {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814},
                {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017},
                {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826},
                {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708},
                {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336},
                {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873},
                {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535}
        };

        double[] s = {3.6392869, 3.0965326, 2.4131673, 1.6285557, 0.7495616};

        double[][] U = {
                {-0.68672751, -0.47077690, -0.27062524, 0.30518577, 0.35585700},
                {-0.28422169, 0.33969351, -0.56700359, -0.38788214, -0.15789372},
                {0.18880503, 0.39049353, -0.26448028, 0.50872376, 0.42411327},
                {-0.56957699, 0.56761727, 0.58111879, 0.11662686, -0.01347444},
                {-0.06682433, -0.04559753, 0.15586923, -0.68802278, 0.60990585},
                {-0.23677832, -0.29935481, 0.09428368, -0.03224480, -0.50781217},
                {0.16440378, -0.31082218, 0.40550635, 0.09794049, 0.19627380}
        };

        double[][] V = {
                {-0.10646320, -0.668422750, -0.33744231, -0.1953744, -0.6243702},
                {0.48885825, 0.546411345, -0.57018018, -0.2348795, -0.2866678},
                {-0.26164973, 0.002221196, -0.01788181, -0.9049532, 0.3350739},
                {-0.02353895, 0.306904408, 0.68247803, -0.2353931, -0.6197333},
                {0.82502638, -0.400562630, 0.30810911, -0.1797507, 0.1778750}
        };

        int m = A.length;
        int n = A[0].length;
        int k = 3;

        Matrix.SVD svd = ARPACK.svd(new Matrix(A),k);
        for (int i = 0; i < k; i++) {
            assertEquals(s[i], svd.s[i], 1E-5);
        }

        assertEquals(m, svd.U.nrow());
        assertEquals(k, svd.U.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(n, svd.V.nrow());
        assertEquals(k, svd.V.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }
}