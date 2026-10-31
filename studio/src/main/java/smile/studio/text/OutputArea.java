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
package smile.studio.text;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.*;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import org.fife.ui.rsyntaxtextarea.LinkGenerator;
import org.fife.ui.rsyntaxtextarea.LinkGeneratorResult;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import smile.plot.swing.Palette;
import smile.studio.SmileStudio;
import smile.studio.workspace.Workspace;

/**
 * Text area for output stream.
 *
 * @author Haifeng Li
 */
public class OutputArea extends ThemedTextArea implements HyperlinkListener, LinkGenerator {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OutputArea.class);
    private final DefaultHighlightPainter painter = new DefaultHighlightPainter(Palette.LIGHT_PINK);
    private final Pattern pattern = Pattern.compile("ERROR|WARN|Recoverable issue|Rejected snippet|Unresolved dependencies|Exception:");
    /**
     * Pattern matching candidate file paths or file:// URLs inside a line of output.
     * Matches Windows drive paths (e.g. C:/code/smile/.smile/memory/2026-10-29.md),
     * Unix/home paths (/..., ~/...), file:// URLs, or relative paths containing '/' or '\'.
     */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?:file:///[^\\s`\"'<>()\\[\\]{}]+)"
            + "|(?:[A-Za-z]:[\\\\/][^\\s`\"'<>()\\[\\]{}]+)"
            + "|(?:~[\\\\/][^\\s`\"'<>()\\[\\]{}]+)"
            + "|(?:(?<![A-Za-z0-9_])[.]{0,2}[\\\\/][^\\s`\"'<>()\\[\\]{}]+)"
            + "|(?:(?<![A-Za-z0-9_./\\\\])[A-Za-z0-9_.-]+(?:[\\\\/][A-Za-z0-9_.-]+)+)"
    );
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
            "https?://[^\\s`\"'<>()\\[\\]{}]+"
    );
    /** The output buffer. StringBuffer is multi-thread safe while StringBuilder isn't. */
    final StringBuffer buffer = new StringBuffer();
    /** Optional custom handler for opening resolved local file paths (used for testing or custom routing). */
    private Consumer<Path> fileOpenHandler;
    /** Tracks the link or token currently hovered without modifier keys so plain click works. */
    private LinkGeneratorResult hoveredLink;

    /**
     * Constructor.
     */
    public OutputArea() {
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);

        // RSyntaxTextArea related settings.
        setSyntaxEditingStyle(SYNTAX_STYLE_MARKDOWN);
        setHighlightCurrentLine(false);
        // Set transparent background
        setBackground(new Color(255, 255, 255, 0));
        setOpaque(false);
        // Enable hyperlink support
        setHyperlinksEnabled(true);
        setLinkGenerator(this);
        addHyperlinkListener(this);
        installDirectLinkClickSupport();
    }

    /**
     * Sets a custom handler invoked when a local file hyperlink is activated.
     * When {@code null} (the default), the file is opened in the enclosing {@link Workspace}.
     *
     * @param handler the file open callback, or {@code null} for default Workspace routing.
     */
    public void setFileOpenHandler(Consumer<Path> handler) {
        this.fileOpenHandler = handler;
    }

    private void installDirectLinkClickSupport() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!getHyperlinksEnabled()) {
                    return;
                }
                int pos = viewToModel2D(e.getPoint());
                LinkGeneratorResult res = pos >= 0 ? isLinkAtOffset(OutputArea.this, pos) : null;
                if (res == null && pos >= 0) {
                    Token t = viewToToken(e.getPoint());
                    if (t != null && t.isHyperlink()) {
                        String lexeme = t.getLexeme();
                        int tokenOffset = t.getOffset();
                        res = createUrlLinkResult(tokenOffset, lexeme);
                    }
                }
                hoveredLink = res;
                Cursor target = res != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
                if (getCursor() != target) {
                    setCursor(target);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredLink = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) {
                    return;
                }
                // If Ctrl/Cmd was held, RSyntaxTextArea's built-in listener already fired ACTIVATED.
                int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
                if ((e.getModifiersEx() & mask) == mask) {
                    return;
                }
                // Do not trigger if the user selected text.
                if (getSelectionStart() != getSelectionEnd()) {
                    return;
                }
                int pos = viewToModel2D(e.getPoint());
                LinkGeneratorResult res = pos >= 0 ? isLinkAtOffset(OutputArea.this, pos) : hoveredLink;
                if (res == null && pos >= 0) {
                    Token t = viewToToken(e.getPoint());
                    if (t != null && t.isHyperlink()) {
                        res = createUrlLinkResult(t.getOffset(), t.getLexeme());
                    }
                }
                if (res != null) {
                    HyperlinkEvent event = res.execute();
                    if (event != null) {
                        hyperlinkUpdate(event);
                    }
                }
            }
        };
        addMouseMotionListener(adapter);
        addMouseListener(adapter);
    }

    /**
     * Returns the output buffer.
     * @return the output buffer.
     */
    public StringBuffer buffer() {
        return buffer;
    }

    /**
     * Flushes the buffer to the text area.
     */
    public void flush() {
        SwingUtilities.invokeLater(() -> setText(buffer.toString()));
    }

    /**
     * Clears the text content and buffer.
     */
    public void clear() {
        buffer.setLength(0);
        SwingUtilities.invokeLater(() -> setText(""));
    }

    /**
     * Appends a string to the buffer.
     *
     * @param str a string.
     * @return this object.
     */
    public OutputArea print(String str) {
        buffer.append(str);
        return this;
    }

    /**
     * Appends a line to buffer and flush context to the output area.
     *
     * @param str a string.
     * @return this object.
     */
    public OutputArea println(String str) {
        buffer.append(str).append('\n');
        flush();
        return this;
    }

    /**
     * Appends a newline to buffer and flush context to the output area.
     *
     * @return this object.
     */
    public OutputArea println() {
        buffer.append('\n');
        flush();
        return this;
    }

    /**
     * Highlights lines with errors or issues.
     */
    public void highlight() {
        String text = getText();
        try {
            var highlighter = getHighlighter();
            String[] lines = text.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                var matcher = pattern.matcher(lines[i]);
                if (matcher.find()) {
                    int startOffset = getLineStartOffset(i);
                    int endOffset = getLineEndOffset(i);
                    highlighter.addHighlight(startOffset, endOffset, painter);
                }
            }
        } catch (BadLocationException ex) {
            logger.warn("Failed to add highlight: {}", ex.getMessage());
        }
    }

    @Override
    public LinkGeneratorResult isLinkAtOffset(RSyntaxTextArea textArea, int offset) {
        if (offset < 0 || offset >= textArea.getDocument().getLength()) {
            return null;
        }
        try {
            int line = textArea.getLineOfOffset(offset);
            int lineStart = textArea.getLineStartOffset(line);
            int lineEnd = textArea.getLineEndOffset(line);
            String lineText = textArea.getText(lineStart, lineEnd - lineStart);
            int relOffset = offset - lineStart;

            // 1. Check for http/https URLs covering relOffset
            Matcher urlMatcher = HTTP_URL_PATTERN.matcher(lineText);
            while (urlMatcher.find()) {
                int start = urlMatcher.start();
                int end = trimTrailingPunctuation(lineText, start, urlMatcher.end());
                if (relOffset >= start && relOffset < end) {
                    int sourceOffset = tokenStartOffset(textArea, lineStart + start, offset);
                    return createUrlLinkResult(sourceOffset, lineText.substring(start, end));
                }
            }

            // 2. Check for local file paths covering relOffset
            Matcher pathMatcher = FILE_PATH_PATTERN.matcher(lineText);
            while (pathMatcher.find()) {
                int start = pathMatcher.start();
                int end = trimTrailingPunctuation(lineText, start, pathMatcher.end());
                if (relOffset >= start && relOffset < end) {
                    String candidate = lineText.substring(start, end);
                    Optional<Path> resolved = resolveExistingFile(candidate);
                    if (resolved.isPresent()) {
                        Path targetPath = resolved.get();
                        int sourceOffset = tokenStartOffset(textArea, lineStart + start, offset);
                        return new LinkGeneratorResult() {
                            @Override
                            public HyperlinkEvent execute() {
                                try {
                                    URL url = targetPath.toUri().toURL();
                                    return new HyperlinkEvent(
                                            OutputArea.this,
                                            HyperlinkEvent.EventType.ACTIVATED,
                                            url,
                                            targetPath.toString());
                                } catch (MalformedURLException ex) {
                                    return new HyperlinkEvent(
                                            OutputArea.this,
                                            HyperlinkEvent.EventType.ACTIVATED,
                                            null,
                                            targetPath.toString());
                                }
                            }

                            @Override
                            public int getSourceOffset() {
                                return sourceOffset;
                            }
                        };
                    }
                }
            }
        } catch (BadLocationException ex) {
            logger.debug("Could not inspect link at offset {}: {}", offset, ex.getMessage());
        }
        return null;
    }

    private LinkGeneratorResult createUrlLinkResult(int sourceOffset, String rawUrl) {
        String normalized = rawUrl.startsWith("www.") ? "http://" + rawUrl : rawUrl;
        return new LinkGeneratorResult() {
            @Override
            public HyperlinkEvent execute() {
                try {
                    return new HyperlinkEvent(
                            OutputArea.this,
                            HyperlinkEvent.EventType.ACTIVATED,
                            URI.create(normalized).toURL(),
                            normalized);
                } catch (Exception ex) {
                    return new HyperlinkEvent(
                            OutputArea.this,
                            HyperlinkEvent.EventType.ACTIVATED,
                            null,
                            normalized);
                }
            }

            @Override
            public int getSourceOffset() {
                return sourceOffset;
            }
        };
    }

    private static int tokenStartOffset(RSyntaxTextArea textArea, int matchDocStart, int hoverOffset) {
        Token t = textArea.modelToToken(hoverOffset);
        if (t != null && t.getOffset() >= 0) {
            return t.getOffset();
        }
        return matchDocStart;
    }

    private static int trimTrailingPunctuation(String text, int start, int end) {
        int cur = end;
        while (cur > start) {
            char c = text.charAt(cur - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == ')' || c == ']' || c == '!' || c == '?') {
                cur--;
            } else {
                break;
            }
        }
        return cur;
    }

    /**
     * Resolves a candidate path string from agent or transcript output to an existing
     * regular file on disk, checking absolute paths, {@code file://} URIs, {@code ~/},
     * the current working directory ({@code user.dir}), and {@code smile.home}.
     *
     * @param candidate raw path token from the text area.
     * @return the normalized absolute {@link Path} if it exists as a regular file.
     */
    public static Optional<Path> resolveExistingFile(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        String raw = candidate.trim();
        // Strip optional trailing :line or :line:col suffix (e.g., Foo.java:42)
        String cleaned = stripLineColumnSuffix(raw);

        try {
            if (cleaned.startsWith("file:/")) {
                Path p = Path.of(URI.create(cleaned)).toAbsolutePath().normalize();
                if (Files.isRegularFile(p)) {
                    return Optional.of(p);
                }
            }
        } catch (Exception ignored) {
        }

        if (cleaned.startsWith("~/") || cleaned.startsWith("~\\")) {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                Optional<Path> p = tryRegularFile(Path.of(userHome).resolve(cleaned.substring(2)));
                if (p.isPresent()) {
                    return p;
                }
            }
        }

        try {
            Path path = Path.of(cleaned);
            if (path.isAbsolute()) {
                return tryRegularFile(path);
            }

            // Try relative to current working directory
            Path cwd = Path.of(System.getProperty("user.dir", "."));
            Optional<Path> inCwd = tryRegularFile(cwd.resolve(path));
            if (inCwd.isPresent()) {
                return inCwd;
            }

            // Try relative to smile.home if configured
            String smileHome = System.getProperty("smile.home");
            if (smileHome != null && !smileHome.isBlank()) {
                Optional<Path> inHome = tryRegularFile(Path.of(smileHome).resolve(path));
                if (inHome.isPresent()) {
                    return inHome;
                }
            }
        } catch (InvalidPathException ignored) {
        }
        return Optional.empty();
    }

    private static String stripLineColumnSuffix(String pathStr) {
        // Preserve Windows drive letter colon (index 1, e.g. C:/...)
        int lastColon = pathStr.lastIndexOf(':');
        while (lastColon > 1) {
            String tail = pathStr.substring(lastColon + 1);
            if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                pathStr = pathStr.substring(0, lastColon);
                lastColon = pathStr.lastIndexOf(':');
            } else {
                break;
            }
        }
        return pathStr;
    }

    private static Optional<Path> tryRegularFile(Path p) {
        try {
            Path normalized = p.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return Optional.of(normalized);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Workspace findWorkspace() {
        Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, this);
        if (ws != null) {
            return ws;
        }
        for (Window window : Window.getWindows()) {
            if (window instanceof SmileStudio studio) {
                Workspace found = findWorkspaceIn(studio.getContentPane());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Workspace findWorkspaceIn(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof Workspace ws) {
                return ws;
            }
            if (comp instanceof Container child) {
                Workspace found = findWorkspaceIn(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            URL url = e.getURL();
            String desc = e.getDescription();

            // 1. Check if this link targets a local file (via file: URL or description path)
            Optional<Path> localFile = Optional.empty();
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                try {
                    localFile = tryRegularFile(Path.of(url.toURI()));
                } catch (Exception ignored) {
                }
            }
            if (localFile.isEmpty() && desc != null && !desc.isBlank()) {
                localFile = resolveExistingFile(desc);
            }

            if (localFile.isPresent()) {
                Path path = localFile.get();
                if (fileOpenHandler != null) {
                    fileOpenHandler.accept(path);
                    return;
                }
                Workspace ws = findWorkspace();
                if (ws != null) {
                    ws.openFile(path);
                    return;
                }
            }

            // 2. Otherwise open HTTP/HTTPS URL in the system's default browser
            if (url != null) {
                try {
                    Desktop.getDesktop().browse(url.toURI());
                } catch (IOException | URISyntaxException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Cannot open URL: " + url,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
