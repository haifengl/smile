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
 * Daubechies wavelets. The Daubechies wavelets are a family of orthogonal
 * wavelets defining a discrete wavelet transform and characterized by a
 * maximal number of vanishing moments for some given support.
 * <p>
 * In general the Daubechies wavelets are chosen to have the highest number A
 * of vanishing moments, (this does not imply the best smoothness) for give
 * n support width N = 2A, and among the 2A?1 possible solutions the one is
 * chosen whose scaling filter has extremal phase. The wavelet transform is
 * also easy to put into practice using the fast wavelet transform. Daubechies
 * wavelets are widely used in solving a broad range of problems,
 * e.g. self-similarity properties of a signal or fractal problems, signal
 * discontinuities, etc.
 * <p>
 * The Daubechies wavelets are not defined in terms of the resulting scaling
 * and wavelet functions; in fact, they are not possible to write down in
 * closed form.
 * <p>
 * Daubechies orthogonal wavelets D2-D20 (even index numbers only) are commonly
 * used. The index number refers to the number N of coefficients. Each wavelet
 * has a number of zero moments or vanishing moments equal to half the number
 * of coefficients. For example, D2 (the Haar wavelet) has one vanishing
 * moment, D4 has two, etc. A vanishing moment limits the wavelet's ability
 * to represent polynomial behaviour or information in a signal. For example,
 * D2, with one moment, easily encodes polynomials of one coefficient, or
 * constant signal components. D4 encodes polynomials with two coefficients,
 * i.e. constant and linear signal components; and D6 encodes 3-polynomials,
 * i.e. constant, linear and quadratic signal components. This ability to
 * encode signals is nonetheless subject to the phenomenon of scale leakage,
 * and the lack of shift-invariance, which arise from the discrete shifting
 * operation during application of the transform. Sub-sequences which represent
 * linear, quadratic (for example) signal components are treated differently
 * by the transform depending on whether the points align with even- or
 * odd-numbered locations in the sequence. The lack of the important property
 * of shift-invariance, has led to the development of several different versions
 * of a shift-invariant (discrete) wavelet transform.
 *
 * @author Haifeng Li
 */
public class DaubechiesWavelet extends Wavelet {
    /**
     * D4 coefficients
     */
    private static final double[] c4 = {
        0.4829629131445341,  0.8365163037378079,
        0.2241438680420134, -0.1294095225512604
    };

    /**
     * D6 coefficients
     */
    private static final double[] c6 = {
         0.3326705529500825,  0.8068915093110924, 0.4598775021184914,
        -0.1350110200102546, -0.0854412738820267, 0.0352262918857095
    };

    /**
     * D8 coefficients
     */
    private static final double[] c8 = {
         0.2303778133088964,  0.7148465705529155, 0.6308807679398587,
        -0.0279837694168599, -0.1870348117190931, 0.0308413818355607,
         0.0328830116668852, -0.010597401785069
    };

    /**
     * D10 coefficients
     */
    private static final double[] c10 = {
        0.1601023979741929,  0.6038292697971895,  0.7243085284377726,
        0.1384281459013203, -0.2422948870663823, -0.0322448695846381,
        0.0775714938400459, -0.0062414902127983, -0.012580751999082,
        0.0033357252854738
    };

    /**
     * D12 coefficients
     */
    private static final double[] c12 = {
        0.111540743350,  0.494623890398,  0.751133908021,
        0.315250351709, -0.226264693965, -0.129766867567,
        0.097501605587,  0.027522865530, -0.031582039318,
        0.000553842201,  0.004777257511, -0.001077301085
    };

    /**
     * D14 coefficients
     */
    private static final double[] c14 = {
         0.0778520540850037,  0.3965393194818912,  0.7291320908461957,
         0.4697822874051889, -0.1439060039285212, -0.2240361849938412,
         0.0713092192668272,  0.0806126091510774, -0.0380299369350104,
        -0.0165745416306655,  0.0125509985560986,  4.295779729214E-4,
        -0.0018016407040473,  3.537137999745E-4
    };

    /**
     * D16 coefficients
     */
    private static final double[] c16 = {
         0.0544158422431072, 0.3128715909143166,  0.6756307362973195,
         0.585354683654216, -0.0158291052563823, -0.2840155429615824,
         4.724845739124E-4,  0.1287474266204893, -0.017369301001809,
        -0.0440882539307971, 0.0139810279174001,  0.0087460940474065,
        -0.004870352993452, -3.91740373377E-4,    6.754494064506E-4,
        -1.174767841248E-4
    };

    /**
     * D18 coefficients
     */
    private static final double[] c18 = {
         0.0380779473638778,  0.2438346746125858,  0.6048231236900955,
         0.6572880780512736,  0.1331973858249883, -0.2932737832791663,
        -0.0968407832229492,  0.1485407493381256,  0.0307256814793365,
        -0.0676328290613279,  2.50947114834E-4,    0.0223616621236798,
        -0.0047232047577518, -0.0042815036824635,  0.0018476468830563,
         2.303857635232E-4,  -2.519631889427E-4,   3.93473203163E-5
    };

    /**
     * D20 coefficients
     */
    private static final double[] c20 = {
         0.026670057901,  0.188176800078,  0.527201188932,
         0.688459039454,  0.281172343661, -0.249846424327,
        -0.195946274377,  0.127369340336,  0.093057364604,
        -0.071394147166, -0.029457536822,  0.033212674059,
         0.003606553567, -0.010733175483,  0.001395351747,
         0.001992405295, -0.000685856695, -0.000116466855,
         0.000093588670, -0.000013264203
    };

    /**
     * Constructor. Create a Daubechies wavelet with n coefficients.
     * n = 4, 6, 8, 10, 12, 14, 16, 18, or 20 are supported. For n = 4,
     * D4Wavelet can be used instead.
     */
    public DaubechiesWavelet(int n) {
        super(n == 4 ? c4 :
              n == 6 ? c6 :
              n == 8 ? c8 :
              n == 10 ? c10 :
              n == 12 ? c12 :
              n == 14 ? c14 :
              n == 16 ? c16 :
              n == 18 ? c18 :
              n == 20 ? c20 : null
            );

        if ( n < 4 || n > 20 || n % 2 != 0) {
            throw new IllegalArgumentException(String.format("n = %d not yet implemented.", n));
        }
    }
}
