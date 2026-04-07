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
package smile.studio.text;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.Point;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.fife.ui.autocomplete.*;
import smile.swing.SmileUtilities;

/**
 * LSP based auto-completion provider that also listens for document changes
 * and notifies the LSP server via {@code textDocument/didChange}.
 *
 * <p>Register an instance of this class as a {@link DocumentListener} on the
 * editor's {@link javax.swing.text.Document} so that every insert or remove
 * event is forwarded to the language server:
 * <pre>{@code
 *   editor.getDocument().addDocumentListener(provider);
 * }</pre>
 *
 * @author Haifeng Li
 */
public class LspCompletionProvider extends AbstractCompletionProvider implements DocumentListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LspCompletionProvider.class);
    private final TextDocumentService docService;
    private final String fileUri;
    /** Monotonically increasing document version sent with every didChange notification. */
    private final AtomicInteger version = new AtomicInteger(0);

    /**
     * Constructor.
     * @param docService the LSP document service for completions.
     * @param fileUri the URI of the file being edited (e.g., "file:///path/to/file.py").
     */
    public LspCompletionProvider(TextDocumentService docService, String fileUri) {
        this.docService = docService;
        this.fileUri = fileUri;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        sendDidChange(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        sendDidChange(e);
    }

    /**
     * {@inheritDoc}
     * <p>Attribute-only changes do not alter text content, so we ignore them.
     */
    @Override
    public void changedUpdate(DocumentEvent e) {
        // Attribute changes (e.g. style) don't modify text; nothing to send.
    }

    /**
     * Sends a {@code textDocument/didOpen} notification to the language server
     * with the current full content of the given editor's document.
     *
     * <p>Call this once after the editor has been populated with the initial
     * file content so the language server can start tracking the document.
     *
     * @param editor the text component whose document content should be sent.
     * @param languageId the LSP language identifier (e.g. {@code "python"}, {@code "scala"}).
     */
    public void open(JTextComponent editor, String languageId) {
        try {
            String text = editor.getText();
            TextDocumentItem item = new TextDocumentItem(fileUri, languageId, version.incrementAndGet(), text);
            docService.didOpen(new DidOpenTextDocumentParams(item));
        } catch (Exception ex) {
            logger.warn("Failed to send didOpen notification: {}", ex.getMessage());
        }
    }


    /**
     * Builds a {@code textDocument/didChange} notification from a
     * Swing {@link JTextComponent} and dispatches it to the language server.
     *
     * @param editor the Swing text component with new text.
     */
    public void change(JTextComponent editor) {
        try {
            // Retrieve the new text that was inserted (empty string for removals).
            String text = editor.getText();
            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent(text);
            VersionedTextDocumentIdentifier versionedId = new VersionedTextDocumentIdentifier(fileUri, version.incrementAndGet());
            DidChangeTextDocumentParams params = new DidChangeTextDocumentParams(versionedId, List.of(change));
            docService.didChange(params);
        }catch (Exception ex) {
            logger.warn("Failed to send didChange notification: {}", ex.getMessage());
        }
    }

    /**
     * Builds an incremental {@code textDocument/didChange} notification from a
     * Swing {@link DocumentEvent} and dispatches it to the language server.
     *
     * @param e the Swing document event describing the change.
     */
    private void sendDidChange(DocumentEvent e) {
        try {
            Document doc = e.getDocument();
            int offset = e.getOffset();
            int length = e.getLength();

            // Determine the LSP start position of the changed region.
            Element root = doc.getDefaultRootElement();
            int startLine = root.getElementIndex(offset);
            int startCol  = offset - root.getElement(startLine).getStartOffset();

            // For a removal the text is already gone; reconstruct end position
            // from the original length stored in the event.
            // For an insertion the "range" collapses to a point (start == end).
            int endOffset = offset + (e.getType() == DocumentEvent.EventType.REMOVE ? length : 0);
            int endLine = root.getElementIndex(endOffset);
            int endCol  = endOffset - root.getElement(endLine).getStartOffset();

            // Retrieve the new text that was inserted (empty string for removals).
            String newText = e.getType() == DocumentEvent.EventType.INSERT
                    ? doc.getText(offset, length)
                    : "";

            TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
            change.setRange(new Range(new Position(startLine, startCol),
                                      new Position(endLine, endCol)));
            change.setText(newText);

            VersionedTextDocumentIdentifier versionedId = new VersionedTextDocumentIdentifier(fileUri, version.incrementAndGet());
            DidChangeTextDocumentParams params = new DidChangeTextDocumentParams(versionedId, List.of(change));
            docService.didChange(params);
        } catch (BadLocationException ex) {
            logger.warn("Failed to compute document change range: {}", ex.getMessage());
        } catch (Exception ex) {
            logger.warn("Failed to send didChange notification: {}", ex.getMessage());
        }
    }

    @Override
    public List<ParameterizedCompletion> getParameterizedCompletions(JTextComponent editor) {
        // If no completions are available, this may be null.
        return null;
    }

    @Override
    public List<Completion> getCompletionsAt(JTextComponent editor, Point p) {
        return List.of();
    }

    @Override
    public String getAlreadyEnteredText(JTextComponent editor) {
        try {
            int dot = editor.getCaretPosition();
            int line = SmileUtilities.getLineOfOffset(editor, dot);
            int start = SmileUtilities.getOffsetOfLine(editor, line);
            return editor.getText(start, dot - start);
        } catch (Exception e) {
            logger.debug("Failed to get already entered text: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public List<Completion> getCompletions(JTextComponent editor) {
        List<Completion> completions = new ArrayList<>();

        try {
            // Prepare CompletionParams (Line and Character are 0-indexed in LSP)
            int dot = editor.getCaretPosition();
            int line = SmileUtilities.getLineOfOffset(editor, dot);
            int column = dot - SmileUtilities.getOffsetOfLine(editor, line);

            CompletionParams params = new CompletionParams();
            params.setTextDocument(new TextDocumentIdentifier(fileUri));
            params.setPosition(new Position(line, column));

            // Call Language Server (Asynchronous to Synchronous bridge)
            // Note: In a production app, use a timeout to avoid UI freezes
            CompletableFuture<Either<List<CompletionItem>, CompletionList>> future = docService.completion(params);

            Either<List<CompletionItem>, CompletionList> result = future.get(2, TimeUnit.SECONDS);

            // Process Results
            List<CompletionItem> items = result.isLeft() ? result.getLeft() : result.getRight().getItems();

            for (CompletionItem item : items) {
                // Map LSP CompletionItem to RSTA Completion
                var completion = new DotCompletion(this,
                        item.getInsertText() != null ? item.getInsertText() : item.getLabel());
                completion.setSummary(item.getDetail());
                completions.add(completion);
            }

        } catch (Exception e) {
            logger.debug("Completion failed: {}", e.getMessage());
        }

        return completions;
    }

    /** A Completion implementation to proper handling of already entered text. */
    private static class DotCompletion extends BasicCompletion {
        public DotCompletion(CompletionProvider provider, String replacementText) {
            super(provider, replacementText);
        }

        @Override
        public String getAlreadyEntered(JTextComponent editor) {
            try {
                var prefix = SmileUtilities.getWordAt(editor, editor.getCaretPosition());
                return getReplacementText().startsWith(prefix) ? prefix : "";
            } catch (BadLocationException e) {
                return "";
            }
        }
    }
}
