/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.notebook;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Notebook} kernel lifecycle management.
 */
class NotebookTest {

    @Test
    void testUnsupportedLanguageKernelState() {
        Notebook notebook = new Notebook(Path.of("test.unsupported_ext"), Map.of(), k -> {});
        assertEquals(Notebook.KernelState.UNSUPPORTED, notebook.kernelState());
        assertNull(notebook.kernel());
        notebook.close();
    }

    @Test
    void testJavaKernelLifecycleTransitionsToReady() throws Exception {
        Notebook notebook = new Notebook(Path.of("Untitled.jsh"), Map.of(), k -> {});
        assertNotNull(notebook.kernelState());
        assertTrue(notebook.kernelState() == Notebook.KernelState.STARTING
                || notebook.kernelState() == Notebook.KernelState.READY);

        long deadline = System.currentTimeMillis() + 15_000;
        while (notebook.kernelState() != Notebook.KernelState.READY && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertEquals(Notebook.KernelState.READY, notebook.kernelState());
        assertNotNull(notebook.kernel());
        notebook.close();
    }
}
