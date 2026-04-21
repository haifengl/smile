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
 * Unit tests for {@link ImageNet} label/folder lookup tables.
 *
 * @author Haifeng Li
 */
public class ImageNetTest {

    @Test
    public void testGivenImageNetWhenCheckingLabelCountThenIs1000() {
        assertEquals(1000, ImageNet.labels.length);
    }

    @Test
    public void testGivenImageNetWhenCheckingFolderCountThenMatchesLabelCount() {
        assertEquals(ImageNet.labels.length, ImageNet.folders.length);
    }

    @Test
    public void testGivenFolder2TargetWhenApplyingKnownFoldersThenReturnsCorrectClassIndex() {
        assertEquals(515, ImageNet.folder2Target.applyAsInt("n03124170")); // cowboy hat
        assertEquals(388, ImageNet.folder2Target.applyAsInt("n02510455")); // giant panda
    }

    @Test
    public void testGivenLabel2TargetWhenApplyingCowboyHatAliasThenReturnsIndex515() {
        assertEquals(515, ImageNet.label2Target.applyAsInt("cowboy hat"));
        assertEquals(515, ImageNet.label2Target.applyAsInt("ten-gallon hat"));
    }

    @Test
    public void testGivenLabel2TargetWhenApplyingGiantPandaAliasesThenAllReturnIndex388() {
        assertEquals(388, ImageNet.label2Target.applyAsInt("giant panda"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("panda bear"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("coon bear"));
        assertEquals(388, ImageNet.label2Target.applyAsInt("Ailuropoda melanoleuca"));
    }

    @Test
    public void testGivenLabel2IdMapWhenLookingUpPandaStringThenReturnsIndex387() {
        // "panda" appears in class 387 (lesser panda) first due to map collision resolution
        assertEquals(387, ImageNet.label2Target.applyAsInt("panda"));
    }

    @Test
    public void testGivenFolder2IdMapWhenCheckingAllFoldersThenNoMissingEntry() {
        for (String folder : ImageNet.folders) {
            assertNotNull(ImageNet.folder2Id.get(folder),
                    "folder2Id is missing entry for: " + folder);
        }
    }

    @Test
    public void testGivenLabel2IdMapWhenLookingUpFirstLabelThenReturnsZero() {
        // labels[0] = "tench, Tinca tinca"
        assertEquals(0, ImageNet.label2Target.applyAsInt("tench"));
    }
}

