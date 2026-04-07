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

import javax.swing.text.JTextComponent;
import java.awt.Point;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.fife.ui.autocomplete.*;
import smile.swing.SmileUtilities;

/**
 * LSP based auto-completion provider.
 *
 * @author Haifeng Li
 */
public class LspCompletionProvider extends AbstractCompletionProvider {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LspCompletionProvider.class);
    private final TextDocumentService docService;
    private final String fileUri;

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
            return SmileUtilities.getWordAt(editor, editor.getCaretPosition());
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
                // Map LSP CompletionItem to RSTA BasicCompletion
                BasicCompletion c = new BasicCompletion(this,
                        item.getInsertText() != null ? item.getInsertText() : item.getLabel());
                c.setSummary(item.getDetail());
                completions.add(c);
            }

        } catch (Exception e) {
            logger.debug("Completion failed: {}", e.getMessage());
        }

        return completions;
    }
}
