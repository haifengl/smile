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
package smile.studio.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;
import smile.studio.text.Editor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Workspace startup file resolution and syntax highlighting.
 */
public class WorkspaceStartupTest {

    @Test
    public void testResolveWelcomeFile() {
        Path welcome = Workspace.resolveHomeFile("Welcome");
        assertNotNull(welcome);
        assertTrue(Files.exists(welcome), "Welcome file should exist at: " + welcome);
    }

    @Test
    public void testResolveReleaseNotesFile() {
        Path releaseNotes = Workspace.resolveHomeFile("Release Notes");
        assertNotNull(releaseNotes);
        assertTrue(Files.exists(releaseNotes), "Release Notes file should exist at: " + releaseNotes);
    }

    @Test
    public void testSyntaxHighlightingForWelcomeAndReleaseNotes() {
        assertEquals(SyntaxConstants.SYNTAX_STYLE_MARKDOWN, Editor.probeSyntaxStyle(Path.of("Welcome")));
        assertEquals(SyntaxConstants.SYNTAX_STYLE_MARKDOWN, Editor.probeSyntaxStyle(Path.of("Release Notes")));
        assertEquals(SyntaxConstants.SYNTAX_STYLE_MARKDOWN, Editor.probeSyntaxStyle(Path.of("/path/to/Welcome")));
        assertEquals(SyntaxConstants.SYNTAX_STYLE_MARKDOWN, Editor.probeSyntaxStyle(Path.of("/path/to/Release Notes")));
    }

    @Test
    public void testWelcomeAndReleaseNotesContent() throws Exception {
        Path welcome = Workspace.resolveHomeFile("Welcome");
        String welcomeContent = Files.readString(welcome);
        assertTrue(welcomeContent.contains("Welcome to SMILE Studio"));
        assertTrue(welcomeContent.contains("Interactive Polyglot Notebooks"));

        Path releaseNotes = Workspace.resolveHomeFile("Release Notes");
        String releaseNotesContent = Files.readString(releaseNotes);
        assertTrue(releaseNotesContent.contains("SMILE 6.3.0 Release Notes"));
    }
}
