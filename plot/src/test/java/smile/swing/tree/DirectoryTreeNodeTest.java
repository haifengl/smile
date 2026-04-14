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
package smile.swing.tree;

import org.junit.jupiter.api.*;

import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DirectoryTreeNode}.
 */
public class DirectoryTreeNodeTest {

    private Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("smile-tree-test-");
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Delete temp directory tree
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }

    // ── Basic node properties ──────────────────────────────────────────────

    @Test
    public void testDirectoryNodeIsNotLeaf() {
        DirectoryTreeNode node = new DirectoryTreeNode(tempDir);
        assertFalse(node.isLeaf(), "A directory node should not be a leaf");
    }

    @Test
    public void testFileNodeIsLeaf() throws IOException {
        Path file = Files.createTempFile(tempDir, "test-", ".txt");
        DirectoryTreeNode node = new DirectoryTreeNode(file);
        assertTrue(node.isLeaf(), "A file node should be a leaf");
    }

    @Test
    public void testToStringReturnsFileName() {
        DirectoryTreeNode node = new DirectoryTreeNode(tempDir);
        assertEquals(tempDir.getFileName().toString(), node.toString());
    }

    @Test
    public void testPathReturnsOriginalPath() {
        DirectoryTreeNode node = new DirectoryTreeNode(tempDir);
        assertEquals(tempDir, node.path());
    }

    // ── addChildren ────────────────────────────────────────────────────────

    @Test
    public void testAddChildrenPopulatesSubnodes() throws IOException {
        Files.createTempFile(tempDir, "file1-", ".txt");
        Files.createTempFile(tempDir, "file2-", ".txt");
        Path subDir = Files.createTempDirectory(tempDir, "subdir-");

        DirectoryTreeNode root = new DirectoryTreeNode(tempDir);
        DefaultTreeModel model = new DefaultTreeModel(root);
        root.addChildren(model);

        // Should have 3 children (2 files + 1 subdir)
        assertEquals(3, root.getChildCount());
    }

    @Test
    public void testAddChildrenIsIdempotent() throws IOException {
        Files.createTempFile(tempDir, "a-", ".txt");

        DirectoryTreeNode root = new DirectoryTreeNode(tempDir);
        DefaultTreeModel model = new DefaultTreeModel(root);
        root.addChildren(model);
        int first = root.getChildCount();
        root.addChildren(model);   // second call must not add duplicates
        assertEquals(first, root.getChildCount(),
                "addChildren must not insert duplicate nodes");
    }

    @Test
    public void testAddChildrenHidesHiddenFiles() throws IOException {
        Files.createTempFile(tempDir, "visible-", ".txt");
        // Create a hidden file (name starts with '.')
        Files.createFile(tempDir.resolve(".hidden"));

        DirectoryTreeNode root = new DirectoryTreeNode(tempDir);
        DefaultTreeModel model = new DefaultTreeModel(root);
        root.addChildren(model);

        // Only the visible file should appear.
        assertEquals(1, root.getChildCount(),
                "Hidden files (starting with '.') must be excluded");
    }

    @Test
    public void testAddChildrenDirectoriesComeBefore() throws IOException {
        Files.createTempFile(tempDir, "aaa-", ".txt");  // "aaa…" sorts before "zzz…"
        Path subDir = Files.createTempDirectory(tempDir, "zzz-dir-");

        DirectoryTreeNode root = new DirectoryTreeNode(tempDir);
        DefaultTreeModel model = new DefaultTreeModel(root);
        root.addChildren(model);

        // First child should be the directory (dirs come first regardless of name)
        DirectoryTreeNode first = (DirectoryTreeNode) root.getChildAt(0);
        assertTrue(Files.isDirectory(first.path()),
                "Directories should sort before files");
    }

    @Test
    public void testFileNodeAddChildrenDoesNothing() throws IOException {
        Path file = Files.createTempFile(tempDir, "leaf-", ".txt");
        DirectoryTreeNode leaf = new DirectoryTreeNode(file);
        DefaultTreeModel model = new DefaultTreeModel(leaf);
        leaf.addChildren(model);  // should not throw and should not add children
        assertEquals(0, leaf.getChildCount());
    }
}

