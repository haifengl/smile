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
package smile.swing;

import org.junit.jupiter.api.*;
import smile.swing.FileChooser.SimpleFileFilter;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FileChooser} and its inner classes.
 */
public class FileChooserTest {

    // ---- SimpleFileFilter tests -----------------------------------------------

    @Test
    public void testFilterAcceptsDirectory() {
        SimpleFileFilter filter = new SimpleFileFilter("Test", "txt");
        File dir = new File(System.getProperty("java.io.tmpdir"));
        assertTrue(filter.accept(dir), "Directories should always be accepted");
    }

    @Test
    public void testFilterAcceptsMatchingExtension() {
        SimpleFileFilter filter = new SimpleFileFilter("Text", "txt");
        File file = new File("readme.txt");
        assertTrue(filter.accept(file));
    }

    @Test
    public void testFilterAcceptsMatchingExtensionCaseInsensitive() {
        SimpleFileFilter filter = new SimpleFileFilter("Text", "txt");
        File upper = new File("README.TXT");
        assertTrue(filter.accept(upper), "Extension matching should be case-insensitive");
    }

    @Test
    public void testFilterRejectsNonMatchingExtension() {
        SimpleFileFilter filter = new SimpleFileFilter("Text", "txt");
        File file = new File("image.png");
        assertFalse(filter.accept(file));
    }

    @Test
    public void testFilterRejectsNull() {
        SimpleFileFilter filter = new SimpleFileFilter("Text", "txt");
        assertFalse(filter.accept(null));
    }

    @Test
    public void testFilterWithMultipleExtensions() {
        SimpleFileFilter filter = new SimpleFileFilter("Code", "java", "scala", "py");
        assertTrue(filter.accept(new File("Foo.java")));
        assertTrue(filter.accept(new File("Bar.scala")));
        assertTrue(filter.accept(new File("baz.py")));
        assertFalse(filter.accept(new File("data.csv")));
    }

    @Test
    public void testFilterDescriptionContainsExtensions() {
        SimpleFileFilter filter = new SimpleFileFilter("Images", "png");
        String desc = filter.getDescription();
        assertTrue(desc.contains("Images"), "Description should contain the user-supplied label");
        assertTrue(desc.contains("png"),    "Description should list the extension");
    }

    @Test
    public void testFilterDescriptionUpdatesAfterAddExtension() {
        SimpleFileFilter filter = new SimpleFileFilter("Data", "csv");
        String before = filter.getDescription();
        filter.addExtension("json");
        String after = filter.getDescription();
        // fullDescription must be regenerated
        assertNotEquals(before, after);
        assertTrue(after.contains("json"));
    }

    @Test
    public void testFilterDotPrefixStripped() {
        // Leading dot should be stripped and matching should still work
        SimpleFileFilter filter = new SimpleFileFilter("Log", ".log");
        assertTrue(filter.accept(new File("app.log")));
    }

    @Test
    public void testReadableImageFilterIsNotNull() {
        assertNotNull(SimpleFileFilter.getReadableImageFilter());
    }

    @Test
    public void testWritableImageFilterIsNotNull() {
        assertNotNull(SimpleFileFilter.getWritableImageFilter());
    }

    // ---- getExtension tests ---------------------------------------------------

    @Test
    public void testGetExtensionReturnsLowerCase() {
        assertEquals("jpg", FileChooser.getExtension(new File("photo.JPG")));
    }

    @Test
    public void testGetExtensionNoDot() {
        assertNull(FileChooser.getExtension(new File("README")));
    }

    @Test
    public void testGetExtensionDotOnly() {
        // "." at position 0 — no extension
        assertNull(FileChooser.getExtension(new File(".gitignore")));
    }

    @Test
    public void testGetExtensionMultipleDots() {
        assertEquals("gz", FileChooser.getExtension(new File("archive.tar.gz")));
    }
}

