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
        assertTrue(releaseNotesContent.contains("Release Notes"));
    }

    @Test
    public void testWorkspaceResourceBundles() {
        java.util.List<java.util.Locale> locales = java.util.List.of(
                java.util.Locale.ROOT,
                java.util.Locale.US,
                java.util.Locale.SIMPLIFIED_CHINESE,
                java.util.Locale.JAPAN,
                java.util.Locale.FRANCE,
                java.util.Locale.of("es", "ES"));

        java.util.List<String> agentNames = java.util.List.of(
                "chief-of-staff",
                "product-manager",
                "data-scientist",
                "architect",
                "desktop-operator",
                "java-coder",
                "python-coder");

        java.util.List<String> outputKeys = java.util.List.of(
                "ChiefOfStaffOutput",
                "DataScientistOutput",
                "ProductManagerOutput",
                "ArchitectOutput",
                "DesktopOperatorOutput",
                "JavaCoderOutput",
                "PythonCoderOutput");

        java.util.List<String> welcomeKeys = java.util.List.of(
                "ChiefOfStaffWelcome",
                "DataScientistWelcome",
                "ProductManagerWelcome",
                "ArchitectWelcome",
                "DesktopOperatorWelcome",
                "JavaCoderWelcome",
                "PythonCoderWelcome");

        for (String name : agentNames) {
            String prefix = Workspace.agentKeyPrefix(name);
            assertTrue(welcomeKeys.contains(prefix + "Welcome"), "Unexpected welcome key for " + name);
            assertTrue(outputKeys.contains(prefix + "Output"), "Unexpected output key for " + name);
        }
        assertEquals("PythonCoder", Workspace.agentKeyPrefix("pythonista"));

        for (java.util.Locale locale : locales) {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("smile.studio.workspace.Workspace", locale);
            assertNotNull(bundle, "Bundle should exist for locale: " + locale);
            for (String key : outputKeys) {
                assertTrue(bundle.containsKey(key), "Missing key " + key + " in locale " + locale);
                String val = bundle.getString(key);
                assertFalse(val.isBlank(), "Empty key " + key + " in locale " + locale);
            }
            for (String key : welcomeKeys) {
                assertTrue(bundle.containsKey(key), "Missing key " + key + " in locale " + locale);
                String val = bundle.getString(key);
                assertFalse(val.isBlank(), "Empty key " + key + " in locale " + locale);
            }
            assertTrue(bundle.containsKey("AgentWelcome"), "Missing AgentWelcome fallback in locale " + locale);
        }
    }
}
