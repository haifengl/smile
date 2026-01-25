/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.manifold;

import smile.datasets.Eurodist;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class IsotonicMDSTest {

    public IsotonicMDSTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws Exception {
        System.out.println("Isotonic MDS");
        double[][] points = {
                {2023.0068, 1868.3016},
                {-781.8541, 596.8077},
                {46.2793, -429.2891},
                {-156.6249, -527.8012},
                {-514.1422, -506.6482},
                {327.9464, -499.7061},
                {719.5708, -1083.3326},
                {-17.1062, 303.5104},
                {-1945.312, 799.1847},
                {582.8252, -784.9216},
                {148.3789, -687.7158},
                {-1969.6474, 153.4056},
                {-203.7808, 211.0122},
                {-1404.4826, 325.0323},
                {-276.8628, 543.1371},
                {297.7253, 460.3404},
                {621.2816, 87.5659},
                {-209.4547, -258.9948},
                {690.2377, 1045.1831},
                {952.4447, -1774.7317},
                {1069.571, 159.6601}
        };

        var euro = new Eurodist();
        IsotonicMDS mds = IsotonicMDS.fit(euro.x());
        assertEquals(0.05846, mds.stress(), 1E-5);

        double[][] coordinates = mds.coordinates();
        double sign0 = Math.signum(points[0][0] * coordinates[0][0]);
        double sign1 = Math.signum(points[0][1] * coordinates[0][1]);
        for (int i = 0; i < points.length; i++) {
            points[i][0] *= sign0;
            points[i][1] *= sign1;
            assertArrayEquals(points[i], coordinates[i], 1E-4);
        }
    }
}
