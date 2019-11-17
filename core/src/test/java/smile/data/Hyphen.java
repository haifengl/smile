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

package smile.data;

/**
 *
 * @author Haifeng
 */
public class Hyphen {

    public static int p;
    public static int k;
    public static Tuple[][] seq;
    public static int[][] label;
    public static int[][] x;
    public static int[] y;
    public static Tuple[][] testSeq;
    public static int[][] testLabel;
    public static int[][] testx;
    public static int[] testy;

    static {
        try {
            Sequence.Dataset train = Sequence.read("sequence/sparse.hyphen.6.train");
            Sequence.Dataset test = Sequence.read("sequence/sparse.hyphen.6.test");
            p = train.p;
            k = train.k;
            x = train.x;
            y = train.y;
            seq = train.seq;
            label = train.label;
            testx = test.x;
            testy = test.y;
            testSeq = test.seq;
            testLabel = test.label;
        } catch (Exception ex) {
            System.err.println("Failed to load 'hyphen': " + ex);
            System.exit(-1);
        }
    }
}
