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

package smile.wavelet;

/**
 * Best localized wavelets.
 *
 * @author Haifeng Li
 */
public class BestLocalizedWavelet extends Wavelet {
    /**
     * BL14 coefficients
     */
    private static final double[] c14 = {
         0.0120154192834842,  0.0172133762994439, -0.0649080035533744,
        -0.0641312898189170,  0.3602184608985549,  0.7819215932965554,
         0.4836109156937821, -0.0568044768822707, -0.1010109208664125,
         0.0447423494687405,  0.0204642075778225, -0.0181266051311065,
        -0.0032832978473081,  0.0022918339541009
    };

    /**
     * BL18 coefficients
     */
    private static final double[] c18 = {
         0.0002594576266544, -0.0006273974067728, -0.0019161070047557,
         0.0059845525181721,  0.0040676562965785, -0.0295361433733604,
        -0.0002189514157348,  0.0856124017265279, -0.0211480310688774,
        -0.1432929759396520,  0.2337782900224977,  0.7374707619933686,
         0.5926551374433956,  0.0805670008868546, -0.1143343069619310,
        -0.0348460237698368,  0.0139636362487191,  0.0057746045512475
    };

    /**
     * BL20 coefficients
     */
    private static final double[] c20 = {
         0.0008625782242896,  0.0007154205305517, -0.0070567640909701,
         0.0005956827305406,  0.0496861265075979,  0.0262403647054251,
        -0.1215521061578162, -0.0150192395413644,  0.5137098728334054,
         0.7669548365010849,  0.3402160135110789, -0.0878787107378667,
        -0.0670899071680668,  0.0338423550064691, -0.0008687519578684,
        -0.0230054612862905, -0.0011404297773324,  0.0050716491945793,
         0.0003401492622332, -0.0004101159165852
    };

    /**
     * Constructor. Create a Best Localized wavelet with n coefficients.
     * n = 14, 18, or 20 are supported.
     */
    public BestLocalizedWavelet(int n) {
        super(n == 14 ? c14 :
              n == 18 ? c18 :
              n == 20 ? c20 : null
            );

        if ( n != 14 && n != 18 && n != 20) {
            throw new IllegalArgumentException(String.format("n = %d not yet implemented.", n));
        }
    }
}
