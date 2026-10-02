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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.linalg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NativeLibrary} directory resolution and the guards that keep
 * a stale Windows system copy from being selected.
 */
public class NativeLibraryTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY);
    }

    @Test
    void resolveDirPrefersSystemPropertyOverEnvironment(@TempDir Path tmp) throws IOException {
        // The property points at a directory that does contain the library.
        Path dir = Files.createDirectory(tmp.resolve("arpack"));
        Files.createFile(dir.resolve(System.mapLibraryName("arpack")));
        System.setProperty(NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY, dir.toString());

        String resolved = NativeLibrary.resolveDir(
                NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY,
                NativeLibrary.ARPACK_NATIVE_PATH_ENV,
                NativeLibrary.ARPACK_LIBRARY);

        assertEquals(dir.toAbsolutePath().toString(), resolved);
    }

    @Test
    void resolveDirRejectsBlankPropertyAndFallsThrough() {
        // A blank/whitespace property is treated as unset, so resolution falls
        // through to the well-known directories / search path rather than
        // yielding a bogus directory.
        System.setProperty(NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY, "   ");

        String resolved = NativeLibrary.resolveDir(
                NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY,
                "SMILE_TEST_UNSET_ENV_VAR",
                NativeLibrary.ARPACK_LIBRARY);

        assertNotEquals("   ", resolved);
        if (resolved != null) {
            assertTrue(Path.of(resolved).isAbsolute());
        }
    }

    @Test
    void ensureLoadedIsIdempotentAndReportsAbsence(@TempDir Path tmp) {
        // Use a name that can never load: "arpack" may already be loaded
        // process-wide (the ARPACK tests initialize the binding), in which case
        // ensureLoaded correctly reports true regardless of the path.
        String absent = "smile_test_absent_library";
        System.setProperty(NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY, tmp.toString());

        assertFalse(NativeLibrary.ensureLoaded(
                NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY,
                NativeLibrary.ARPACK_NATIVE_PATH_ENV,
                absent));
        assertFalse(NativeLibrary.ensureLoaded(
                NativeLibrary.ARPACK_NATIVE_PATH_PROPERTY,
                NativeLibrary.ARPACK_NATIVE_PATH_ENV,
                absent));
    }

    @Test
    void findLibraryFileReturnsNullWhenAbsent() {
        assertNull(NativeLibrary.findLibraryFile("totally_absent_library_xyz"));
        assertFalse(NativeLibrary.libraryFilePresent("totally_absent_library_xyz"));
    }
}
