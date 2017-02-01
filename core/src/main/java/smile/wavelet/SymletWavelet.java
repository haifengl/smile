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
 * Symlet wavelets. The symlets have compact support and were constructed to
 * be as nearly symmetric (least asymmetric) as possible.
 *
 * @author Haifeng Li
 */
public class SymletWavelet extends Wavelet {
    /**
     * S8 coefficients
     */
    private static final double[] c8 = {
        -0.0757657147893407, -0.0296355276459541,  0.4976186676324578,
         0.8037387518052163,  0.2978577956055422, -0.0992195435769354,
        -0.0126039672622612,  0.0322231006040713
    };

    /**
     * S10 coefficients
     */
    private static final double[] c10 = {
        0.0195388827353869, -0.0211018340249298, -0.1753280899081075,
        0.0166021057644243,  0.6339789634569490,  0.7234076904038076,
        0.1993975339769955, -0.0391342493025834,  0.0295194909260734,
        0.0273330683451645
    };
    
    /**
     * S12 coefficients
     */
    private static final double[] c12 = {
         0.0154041093273377,  0.0034907120843304, -0.1179901111484105,
        -0.0483117425859981,  0.4910559419276396,  0.7876411410287941,
         0.3379294217282401, -0.0726375227866000, -0.0210602925126954,
         0.0447249017707482,  0.0017677118643983, -0.0078007083247650
    };

    /**
     * S14 coefficients
     */
    private static final double[] c14 = {
         0.0102681767084968,  0.0040102448717033, -0.1078082377036168,
        -0.1400472404427030,  0.2886296317509833,  0.7677643170045710,
         0.5361019170907720,  0.0174412550871099, -0.0495528349370410,
         0.0678926935015971,  0.0305155131659062, -0.0126363034031526,
        -0.0010473848889657,  0.0026818145681164
    };

    /**
     * S16 coefficients
     */
    private static final double[] c16 = {
        -0.0033824159513594, -0.0005421323316355,  0.0316950878103452,
         0.0076074873252848, -0.1432942383510542, -0.0612733590679088,
         0.4813596512592012,  0.7771857516997478,  0.3644418948359564,
        -0.0519458381078751, -0.0272190299168137,  0.0491371796734768,
         0.0038087520140601, -0.0149522583367926, -0.0003029205145516,
         0.0018899503329007
    };

    /**
     * S18 coefficients
     */
    private static final double[] c18 = {
         0.0010694900326538, -0.0004731544985879, -0.0102640640276849,
         0.0088592674935117,  0.0620777893027638, -0.0182337707798257,
        -0.1915508312964873,  0.0352724880359345,  0.6173384491413523,
         0.7178970827642257,  0.2387609146074182, -0.0545689584305765,
         0.0005834627463312,  0.0302248788579895, -0.0115282102079848,
        -0.0132719677815332,  0.0006197808890549,  0.0014009155255716
    };

    /**
     * S20 coefficients
     */
    private static final double[] c20 = {
         0.0007701598091030,  0.0000956326707837, -0.0086412992759401,
        -0.0014653825833465,  0.0459272392237649,  0.0116098939129724,
        -0.1594942788575307, -0.0708805358108615,  0.4716906668426588,
         0.7695100370143388,  0.3838267612253823, -0.0355367403054689,
        -0.0319900568281631,  0.0499949720791560,  0.0057649120455518,
        -0.0203549398039460, -0.0008043589345370,  0.0045931735836703,
         0.0000570360843390, -0.0004593294205481
    };

    /**
     * Constructor. Create a Symmlet wavelet with n coefficients.
     * n = 8, 10, 12, 14, 16, 18, or 20 are supported.
     */
    public SymletWavelet(int n) {
        super(n == 8 ? c8 :
              n == 10 ? c10 :
              n == 12 ? c12 :
              n == 14 ? c14 :
              n == 16 ? c16 :
              n == 18 ? c18 :
              n == 20 ? c20 : null
            );

        if ( n < 8 || n > 20 || n % 2 != 0) {
            throw new IllegalArgumentException(String.format("n = %d not yet implemented.", n));
        }
    }
}
