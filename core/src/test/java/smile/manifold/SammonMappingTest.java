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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.manifold;

import java.util.Arrays;
import smile.datasets.Eurodist;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SammonMappingTest {

    public SammonMappingTest() {
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
        System.out.println("Sammon's Mapping");

        double[][] points = {
                {-1921.9111, -1830.4309},
                {759.7598, -606.0879},
                {-80.1989, 443.366},
                {106.2067, 512.101},
                {484.4129, 477.3046},
                {-295.3324, 445.0549},
                {-543.941, 1091.5882},
                {7.4096, -269.6847},
                {1942.8039, -727.8288},
                {-626.7153, 721.5507},
                {-185.8613, 658.2859},
                {1916.6406, -83.5842},
                {149.485, -217.709},
                {1372.7065, -349.7255},
                {285.2568, -514.7278},
                {-273.1086, -426.4983},
                {-569.246, -106.5333},
                {161.4922, 261.1512},
                {-698.6729, -1023.6605},
                {-951.8776, 1716.5056},
                {-1039.3089, -170.4371}
        };

        var euro = new Eurodist();
        SammonMapping sammon = SammonMapping.fit(euro.x());
        assertEquals(0.00941, sammon.stress(), 1E-5);

        for (var point : sammon.coordinates()) {
            System.out.println(Arrays.toString(point));
        }
    }
}
