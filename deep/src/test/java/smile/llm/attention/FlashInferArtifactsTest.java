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
package smile.llm.attention;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hybrid AOT resolution without network download.
 */
public class FlashInferArtifactsTest {
    @TempDir
    Path tmp;

    @Test
    public void testGivenBundledStyleDirWhenResolveThenFindsSo() throws Exception {
        Path aot = tmp.resolve("aot");
        Path mod = aot.resolve("batch_decode_dummy");
        Files.createDirectories(mod);
        Files.writeString(mod.resolve("batch_decode_dummy.so"), "x");
        var found = FlashInferArtifacts.resolve(aot.toString(), null, false, "cu132");
        assertTrue(found.isPresent());
        assertEquals(aot.toAbsolutePath().normalize(), found.get().toAbsolutePath().normalize());
    }

    @Test
    public void testGivenMissingDirWhenResolveThenEmpty() {
        assertTrue(FlashInferArtifacts.resolve(
                tmp.resolve("nope").toString(), null, false, "cu132").isEmpty());
    }

    @Test
    public void testGivenUsableAotWhenIsUsableThenTrue() throws Exception {
        Path aot = tmp.resolve("aot2");
        Files.createDirectories(aot);
        Files.writeString(aot.resolve("x.so"), "x");
        assertTrue(FlashInferArtifacts.isUsableAot(aot));
        assertFalse(FlashInferArtifacts.isUsableAot(tmp.resolve("missing")));
    }
}
