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
package smile.vision;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ImageNetTest {

    public ImageNetTest() {
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
    public void test() {
        assertEquals(515, ImageNet.folder2Target.applyAsInt("n03124170"));
        assertEquals(388, ImageNet.folder2Target.applyAsInt("n02510455"));
        assertEquals(515, ImageNet.label2Target.applyAsInt("cowboy hat"));
        assertEquals(515, ImageNet.label2Target.applyAsInt("ten-gallon hat"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("giant panda"));
        assertEquals(387, ImageNet.label2Target.applyAsInt("panda"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("panda bear"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("coon bear"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("Ailuropoda melanoleuca"));
    }
}