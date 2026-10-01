/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.notebook;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.util.ipynb.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Notebook} kernel lifecycle management and notebook persistence.
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

    @Test
    void testLoadIpynbWithOutputsAndSaveRoundTrip(@TempDir Path tempDir) throws Exception {
        Path ipynbFile = tempDir.resolve("sample.ipynb");

        var streamOut = new StreamOutput("stdout", MultilineString.of("result: 42\n"));
        var codeCell = new CodeCell(
                "custom-cell-id",
                new CellMetadata(false, null, null, null, null, null, "test-cell", List.of("tag1"), null, null),
                MultilineString.of("val x = 42\nprintln(x)"),
                List.of(streamOut),
                7
        );
        var markdownCell = new MarkdownCell(
                "md-cell-id",
                new CellMetadata(),
                MultilineString.of("# Notes"),
                Map.of()
        );
        var metadata = new Metadata(
                new KernelSpec("Java", "java", "java"),
                new LanguageInfo("java", "25", "text/x-java", ".java", null, null, null, null),
                null,
                List.of(),
                null
        );
        var original = new JupyterNotebook(
                new ArrayList<>(List.of(codeCell, markdownCell)),
                metadata,
                5,
                10
        );
        original.write(ipynbFile);

        Notebook notebook = new Notebook(ipynbFile, Map.of(), k -> {});
        try {
            Cell cell0 = notebook.getCell(0);
            assertEquals("custom-cell-id", cell0.id());
            assertEquals(Integer.valueOf(7), cell0.getExecutionCount());
            assertEquals(1, cell0.outputs().size());
            assertInstanceOf(StreamOutput.class, cell0.outputs().get(0));
            assertTrue(cell0.output().getText().contains("result: 42"));

            Cell cell1 = notebook.getCell(1);
            assertEquals("md-cell-id", cell1.id());

            // Save the notebook
            notebook.save();

            // Re-read and verify persistence
            var reloaded = JupyterNotebook.from(ipynbFile);
            assertEquals(2, reloaded.cells().size());

            var reloadedCode = (CodeCell) reloaded.cells().get(0);
            assertEquals("custom-cell-id", reloadedCode.id());
            assertEquals(Integer.valueOf(7), reloadedCode.executionCount());
            assertEquals(1, reloadedCode.outputs().size());
            var reloadedStream = (StreamOutput) reloadedCode.outputs().get(0);
            assertEquals("stdout", reloadedStream.name());
            assertEquals("result: 42\n", reloadedStream.text().value());
            assertNotNull(reloadedCode.metadata());
            assertEquals("test-cell", reloadedCode.metadata().name());
        } finally {
            notebook.close();
        }
    }

    @Test
    void testClearOutputAndSave(@TempDir Path tempDir) throws Exception {
        Path ipynbFile = tempDir.resolve("sample_clear.ipynb");

        var streamOut = new StreamOutput("stdout", MultilineString.of("output to clear\n"));
        var codeCell = new CodeCell(
                "cell-to-clear",
                new CellMetadata(),
                MultilineString.of("println(1)"),
                List.of(streamOut),
                3
        );
        var metadata = new Metadata(
                new KernelSpec("Java", "java", "java"),
                new LanguageInfo("java", "25", "text/x-java", ".java", null, null, null, null),
                null,
                List.of(),
                null
        );
        var original = new JupyterNotebook(
                new ArrayList<>(List.of(codeCell)),
                metadata,
                5,
                10
        );
        original.write(ipynbFile);

        Notebook notebook = new Notebook(ipynbFile, Map.of(), k -> {});
        try {
            Cell cell = notebook.getCell(0);
            assertEquals(1, cell.outputs().size());

            // Clear output
            cell.clearOutput();
            assertTrue(cell.outputs().isEmpty());
            assertNull(cell.getExecutionCount());

            notebook.save();

            var reloaded = JupyterNotebook.from(ipynbFile);
            var reloadedCode = (CodeCell) reloaded.cells().get(0);
            assertTrue(reloadedCode.outputs().isEmpty());
            assertNull(reloadedCode.executionCount());
        } finally {
            notebook.close();
        }
    }

    @Test
    void testNewNotebookSaveAsIpynbWithOutputs(@TempDir Path tempDir) throws Exception {
        Path newIpynb = tempDir.resolve("new_notebook.ipynb");

        Notebook notebook = new Notebook(newIpynb, Map.of(), k -> {});
        try {
            Cell cell = notebook.getCell(0);
            cell.editor().setText("int a = 100;");
            cell.setOutputs(List.of(new StreamOutput("stdout", MultilineString.of("a = 100\n"))));
            cell.setExecutionCount(1);

            notebook.save();
            assertTrue(Files.exists(newIpynb));

            var loaded = JupyterNotebook.from(newIpynb);
            assertEquals(5, loaded.nbformat());
            assertNotNull(loaded.metadata());
            assertNotNull(loaded.metadata().kernelspec());
            assertEquals(1, loaded.cells().size());

            var loadedCode = (CodeCell) loaded.cells().get(0);
            assertEquals("int a = 100;", loadedCode.source().value());
            assertEquals(Integer.valueOf(1), loadedCode.executionCount());
            assertEquals(1, loadedCode.outputs().size());
            assertEquals("a = 100\n", ((StreamOutput) loadedCode.outputs().get(0)).text().value());
        } finally {
            notebook.close();
        }
    }

    @Test
    void testCasNotebookSave(@TempDir Path tempDir) throws Exception {
        Path casSource = Path.of("studio/src/universal/notebooks/cas.ipynb");
        Path casCopy = tempDir.resolve("cas.ipynb");
        Files.copy(casSource, casCopy);

        Notebook notebook = new Notebook(casCopy, Map.of(), k -> {});
        try {
            assertTrue(notebook.getCellCount() > 0);
            // Save the notebook
            notebook.save();

            var loaded = JupyterNotebook.from(casCopy);
            assertEquals(notebook.getCellCount(), loaded.cells().size());
        } finally {
            notebook.close();
        }
    }

    @Test
    void testBrokenIpynbOpensWithStarterCell(@TempDir Path tempDir) throws Exception {
        Path broken = tempDir.resolve("broken.ipynb");
        Files.writeString(broken, "{\n  \"cells\" : [\n");

        Notebook notebook = new Notebook(broken, Map.of(), k -> {});
        try {
            // Should not throw and should have initialized a starter cell
            assertEquals(1, notebook.getCellCount());
        } finally {
            notebook.close();
        }
    }
}
