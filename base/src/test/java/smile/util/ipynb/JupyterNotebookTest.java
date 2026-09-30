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
package smile.util.ipynb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JupyterNotebook} serialization and deserialization.
 *
 * @author Haifeng Li
 */
public class JupyterNotebookTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testMultilineString() {
        var mls = MultilineString.of("line 1\nline 2\nline 3");
        assertEquals(3, mls.lines().size());
        assertEquals("line 1\n", mls.lines().get(0));
        assertEquals("line 2\n", mls.lines().get(1));
        assertEquals("line 3", mls.lines().get(2));
        assertEquals("line 1\nline 2\nline 3", mls.value());

        var empty = MultilineString.of("");
        assertTrue(empty.lines().isEmpty());
        assertEquals("", empty.value());

        var single = MultilineString.of("single line");
        assertEquals(1, single.lines().size());
        assertEquals("single line", single.value());
    }

    @Test
    public void testNotebookRoundTripWithOutputs(@TempDir Path tempDir) throws IOException {
        var streamOut = new StreamOutput("stdout", MultilineString.of("Hello, world!\n"));

        Map<String, JsonNode> execData = Map.of("text/plain", mapper.valueToTree("42"));
        var execResult = new ExecuteResultOutput(1, execData, Map.of(), Map.of());

        Map<String, JsonNode> displayData = Map.of("text/plain", mapper.valueToTree("display output"));
        var displayOut = new DisplayDataOutput(displayData, Map.of(), Map.of());

        var errorOut = new ErrorOutput(
                "ZeroDivisionError",
                "division by zero",
                List.of("Traceback (most recent call last):", "ZeroDivisionError: division by zero")
        );

        var codeCell = new CodeCell(
                "cell-1",
                new CellMetadata(false, null, null, null, null, null, "test-code", List.of("tag1"), null, null),
                MultilineString.of("x = 42\nprint('Hello, world!')\nx"),
                List.of(streamOut, execResult, displayOut, errorOut),
                1
        );

        var markdownCell = new MarkdownCell(
                "cell-2",
                new CellMetadata(),
                MultilineString.of("# Title\nSome description"),
                Map.of()
        );

        var rawCell = new RawCell(
                "cell-3",
                new CellMetadata(),
                MultilineString.of("raw content"),
                Map.of()
        );

        var kernelSpec = new KernelSpec("Python 3", "python", "python3");
        var langInfo = new LanguageInfo("python", "3.10", "text/x-python", ".py", null, null, null, null);
        var metadata = new Metadata(kernelSpec, langInfo, "Test Notebook", List.of(), null);

        var notebook = new JupyterNotebook(
                new ArrayList<>(List.of(codeCell, markdownCell, rawCell)),
                metadata,
                JupyterNotebook.NBFORMAT,
                JupyterNotebook.NBFORMAT_MINOR
        );

        Path testFile = tempDir.resolve("test.ipynb");
        notebook.write(testFile);

        assertTrue(Files.exists(testFile));
        assertTrue(Files.size(testFile) > 0);

        var loaded = JupyterNotebook.from(testFile);
        assertEquals(5, loaded.nbformat());
        assertEquals(JupyterNotebook.NBFORMAT_MINOR, loaded.nbformatMinor());
        assertNotNull(loaded.metadata());
        assertEquals("python3", loaded.metadata().kernelspec().name());
        assertEquals(3, loaded.cells().size());

        // Cell 1: CodeCell
        assertInstanceOf(CodeCell.class, loaded.cells().get(0));
        var loadedCode = (CodeCell) loaded.cells().get(0);
        assertEquals("cell-1", loadedCode.id());
        assertEquals(1, loadedCode.executionCount());
        assertEquals("x = 42\nprint('Hello, world!')\nx", loadedCode.source().value());
        assertEquals(4, loadedCode.outputs().size());

        // Outputs
        assertInstanceOf(StreamOutput.class, loadedCode.outputs().get(0));
        var loadedStream = (StreamOutput) loadedCode.outputs().get(0);
        assertEquals("stdout", loadedStream.name());
        assertEquals("Hello, world!\n", loadedStream.text().value());

        assertInstanceOf(ExecuteResultOutput.class, loadedCode.outputs().get(1));
        var loadedExec = (ExecuteResultOutput) loadedCode.outputs().get(1);
        assertEquals(1, loadedExec.executionCount());
        assertEquals("42", loadedExec.data().get("text/plain").asString());

        assertInstanceOf(DisplayDataOutput.class, loadedCode.outputs().get(2));
        var loadedDisplay = (DisplayDataOutput) loadedCode.outputs().get(2);
        assertEquals("display output", loadedDisplay.data().get("text/plain").asString());

        assertInstanceOf(ErrorOutput.class, loadedCode.outputs().get(3));
        var loadedError = (ErrorOutput) loadedCode.outputs().get(3);
        assertEquals("ZeroDivisionError", loadedError.ename());
        assertEquals("division by zero", loadedError.evalue());
        assertEquals(2, loadedError.traceback().size());

        // Cell 2: MarkdownCell
        assertInstanceOf(MarkdownCell.class, loaded.cells().get(1));
        var loadedMd = (MarkdownCell) loaded.cells().get(1);
        assertEquals("cell-2", loadedMd.id());
        assertEquals("# Title\nSome description", loadedMd.source().value());

        // Cell 3: RawCell
        assertInstanceOf(RawCell.class, loaded.cells().get(2));
        var loadedRaw = (RawCell) loaded.cells().get(2);
        assertEquals("cell-3", loadedRaw.id());
        assertEquals("raw content", loadedRaw.source().value());
    }

    @Test
    public void testReadExistingNotebook() throws IOException {
        Path existing = Path.of("studio/src/universal/notebooks/regression.ipynb");
        if (Files.exists(existing)) {
            var nb = JupyterNotebook.from(existing);
            assertNotNull(nb);
            assertFalse(nb.cells().isEmpty());
            for (var cell : nb.cells()) {
                assertNotNull(cell.cellType());
                assertNotNull(cell.source());
            }
        }
    }

    @Test
    public void testCasNotebookRoundTrip(@TempDir Path tempDir) throws IOException {
        Path casPath = Path.of("studio/src/universal/notebooks/cas.ipynb");
        assertTrue(Files.exists(casPath));
        var nb = JupyterNotebook.from(casPath);
        assertNotNull(nb);
        assertFalse(nb.cells().isEmpty());

        Path out = tempDir.resolve("cas_out.ipynb");
        nb.write(out);
        assertTrue(Files.exists(out));

        var loaded = JupyterNotebook.from(out);
        assertEquals(nb.cells().size(), loaded.cells().size());
    }

    @Test
    public void testInvalidJsonThrowsIOException(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("broken.ipynb");
        Files.writeString(broken, "{\n  \"cells\" : [\n");
        assertThrows(IOException.class, () -> JupyterNotebook.from(broken));
    }
}
