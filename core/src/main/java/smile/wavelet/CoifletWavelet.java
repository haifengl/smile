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
 * Coiflet wavelets. Coiflet wavelets have scaling functions with vanishing
 * moments. The wavelet is near symmetric, their wavelet functions have N / 3
 * vanishing moments and scaling functions N / 3 ? 1, and has been used in
 * many applications using Calderon-Zygmund Operators.
 *
 * @author Haifeng Li
 */
public class CoifletWavelet extends Wavelet {
    /**
     * Coiflet6 coefficients
     */
    private static final double[] c6 = {
        -0.0156557285289848, -0.0727326213410511,  0.3848648565381134,
         0.8525720416423900,  0.3378976709511590, -0.0727322757411889
    };

    /**
     * Coiflet12 coefficients
     */
    private static final double[] c12 = {
        -0.0007205494453679, -0.0018232088707116,  0.0056114348194211,
         0.0236801719464464, -0.0594344186467388, -0.0764885990786692,
         0.4170051844236707,  0.8127236354493977,  0.3861100668229939,
        -0.0673725547222826, -0.0414649367819558,  0.0163873364635998
    };

    /**
     * Coiflet18 coefficients
     */
    private static final double[] c18 = {
        -0.0000345997728362, -0.0000709833031381,  0.0004662169601129,
         0.0011175187708906, -0.0025745176887502, -0.0090079761366615,
         0.0158805448636158,  0.0345550275730615, -0.0823019271068856,
        -0.0717998216193117,  0.4284834763776168,  0.7937772226256169,
         0.4051769024096150, -0.0611233900026726, -0.0657719112818552,
         0.0234526961418362,  0.0077825964273254, -0.0037935128644910
    };

    /**
     * Coiflet24 coefficients
     */
    private static final double[] c24 = {
        -0.0000017849850031, -0.0000032596802369,  0.0000312298758654,
         0.0000623390344610, -0.0002599745524878, -0.0005890207562444,
         0.0012665619292991,  0.0037514361572790, -0.0056582866866115,
        -0.0152117315279485,  0.0250822618448678,  0.0393344271233433,
        -0.0962204420340021, -0.0666274742634348,  0.4343860564915321,
         0.7822389309206135,  0.4153084070304910, -0.0560773133167630,
        -0.0812666996808907,  0.0266823001560570,  0.0160689439647787,
        -0.0073461663276432, -0.0016294920126020,  0.0008923136685824
    };

    /**
     * Coiflet30 coefficients
     */
    private static final double[] c30 = {
        -0.0000000951765727, -0.0000001674428858,  0.0000020637618516,
         0.0000037346551755, -0.0000213150268122, -0.0000413404322768,
         0.0001405411497166,  0.0003022595818445, -0.0006381313431115,
        -0.0016628637021860,  0.0024333732129107,  0.0067641854487565,
        -0.0091642311634348, -0.0197617789446276,  0.0326835742705106,
         0.0412892087544753, -0.1055742087143175, -0.0620359639693546,
         0.4379916262173834,  0.7742896037334738,  0.4215662067346898,
        -0.0520431631816557, -0.0919200105692549,  0.0281680289738655,
         0.0234081567882734, -0.0101311175209033, -0.0041593587818186,
         0.0021782363583355,  0.0003585896879330, -0.0002120808398259
    };

    /**
     * Constructor. Create a Coiflet wavelet with n coefficients.
     * n = 6, 12, 18, 24, or 30 are supported.
     */
    public CoifletWavelet(int n) {
        super(n == 6 ? c6 :
              n == 12 ? c12 :
              n == 18 ? c18 :
              n == 24 ? c24 :
              n == 30 ? c30 : null
            );

        if ( n < 6 || n > 30 || n % 6 != 0) {
            throw new IllegalArgumentException(String.format("n = %d not yet implemented.", n));
        }
    }
}
