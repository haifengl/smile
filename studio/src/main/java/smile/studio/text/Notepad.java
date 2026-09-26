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

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.rsta.ui.CollapsibleSectionPanel;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.ReplaceToolBar;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.FindToolBar;
import org.fife.ui.rsyntaxtextarea.spell.SpellingParser;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import smile.studio.StatusBar;
import smile.studio.workspace.OpenFile;

/**
 * A simple text editor. It is hosted as a tab of the workspace tabbed pane.
 *
 * @author Haifeng Li
 */
public final class Notepad extends JPanel implements OpenFile, DocumentListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Notepad.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Notepad.class.getName(), Locale.getDefault());

    // TODO: update to lazy constant with Java 25+ (still preview)
    private static SpellingParser dict = null;
    private Path file;
    private final CollapsibleSectionPanel csp = new CollapsibleSectionPanel();
    private final Editor editor = new Editor(40, 120);
    private final StatusBar statusBar = new StatusBar();
    /** The search context shared by the inline tool bars. */
    private final SearchContext searchContext = new SearchContext();
    private final FindToolBar findToolBar = new FindToolBar(this);
    private final ReplaceToolBar replaceToolBar = new ReplaceToolBar(this);
    private boolean changed = false;

    /**
     * Constructor.
     * @param file the file to open.
     */
    public Notepad(Path file) {
        super(new BorderLayout());
        this.file = file;
        add(csp, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        initSearchDialogs();
        initSearchKeyBindings();

        editor.setFont(Monospaced.getFont());
        editor.setCodeFoldingEnabled(true);
        editor.setMarkOccurrences(true);

        if (dict != null) {
            editor.addParser(dict);
        } else {
            Thread.ofVirtual().name("spelling-dict-loader").start(() -> {
                try {
                    File zip = Path.of(System.getProperty("smile.home"))
                            .resolve("data", "eng_dic.zip")
                            .toFile();
                    dict = SpellingParser.createEnglishSpellingParser(zip, true, false);
                    SwingUtilities.invokeLater(() -> editor.addParser(dict));
                } catch (Exception ex) {
                    logger.error("Failed to load dictionary: {}", ex.getMessage());
                }
            });
        }

        try {
            String content = Files.readString(file);
            editor.setText(content);
            editor.setCaretPosition(0);
            var style = Editor.probeSyntaxStyle(file);
            editor.setSyntaxEditingStyle(style);
            editor.setAutoComplete(file.toUri().toString(), style);
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                    null,
                    ex.getMessage(),
                    bundle.getString("Error"),
                    JOptionPane.ERROR_MESSAGE)
            );
        }

        RTextScrollPane sp = new RTextScrollPane(editor);
        csp.add(sp);

        ErrorStrip errorStrip = new ErrorStrip(editor);
        add(errorStrip, BorderLayout.LINE_END);

        editor.getDocument().addDocumentListener(this);
    }

    @Override
    public String getSelectedText() {
        return editor.getSelectedText();
    }

    /**
     * Creates our Find and Replace tool bars.
     */
    private void initSearchDialogs() {
        // Tie toolbar's search contexts together.
        findToolBar.setSearchContext(searchContext);
        replaceToolBar.setSearchContext(searchContext);
    }

    /**
     * Binds the inline search tool bars and the go-to-line dialog to keyboard
     * shortcuts. The Find and Replace dialogs themselves are owned by the
     * application's Edit menu, which routes them to the selected tab.
     */
    private void initSearchKeyBindings() {
        int ctrl = getToolkit().getMenuShortcutKeyMaskEx();
        int shift = InputEvent.SHIFT_DOWN_MASK;
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrl|shift);
        Action action = csp.addBottomComponent(key, findToolBar);
        action.putValue(Action.NAME, bundle.getString("ShowFindBar"));
        key = KeyStroke.getKeyStroke(KeyEvent.VK_H, ctrl|shift);
        action = csp.addBottomComponent(key, replaceToolBar);
        action.putValue(Action.NAME, bundle.getString("ShowReplaceBar"));

        bind(new GoToLineAction());
    }

    /**
     * Binds an action to its accelerator while this panel or one of its
     * descendants has the focus.
     *
     * @param action the action to bind.
     */
    private void bind(Action action) {
        Object name = action.getValue(Action.NAME);
        getActionMap().put(name, action);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put((KeyStroke) action.getValue(Action.ACCELERATOR_KEY), name);
    }

    /**
     * Returns the window ancestor of this panel, or null if it is not yet
     * displayed. A null owner is accepted by the go-to-line dialog.
     *
     * @return the owning frame.
     */
    private Frame owner() {
        return SwingUtilities.getWindowAncestor(this) instanceof Frame frame ? frame : null;
    }

    /**
     * Listens for events from our search dialogs and actually does the work.
     */
    @Override
    public void searchEvent(SearchEvent e) {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();

        switch (type) {
            case MARK_ALL -> {
                var result = SearchEngine.markAll(editor, context);
                var text = MessageFormat.format(bundle.getString("MarkCount"), result.getMarkedCount());
                SwingUtilities.invokeLater(() -> statusBar.setStatus(text));
            }
            case FIND -> {
                var result = SearchEngine.find(editor, context);
                if (!result.wasFound() || result.isWrapped()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                } else if (context.getMarkAll()) {
                    var text = MessageFormat.format(bundle.getString("MarkCount"), result.getMarkedCount());
                    SwingUtilities.invokeLater(() -> statusBar.setStatus(text));
                }
            }
            case REPLACE -> {
                var result = SearchEngine.replace(editor, context);
                if (!result.wasFound() || result.isWrapped()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                }
            }
            case REPLACE_ALL -> {
                var result = SearchEngine.replaceAll(editor, context);
                JOptionPane.showMessageDialog(
                        this,
                        MessageFormat.format(bundle.getString("ReplaceCount"), result.getCount()));
            }
        }
    }

    /**
     * Opens the "Go to Line" dialog.
     */
    private class GoToLineAction extends AbstractAction {
        GoToLineAction() {
            super(bundle.getString("GoToLine"));
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GoToDialog dialog = new GoToDialog(owner());
            dialog.setMaxLineNumberAllowed(editor.getLineCount());
            dialog.setVisible(true);
            int line = dialog.getLineNumber();
            if (line > 0) {
                try {
                    editor.setCaretPosition(editor.getLineStartOffset(line-1));
                } catch (BadLocationException ex) { // Never happens
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                    logger.error("Failed to set caret position: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Saves the file.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void save() throws IOException {
        Files.writeString(file, editor.getText());
        changed = false;
    }

    /**
     * Re-reads the file from disk, discarding unsaved changes.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void reload() throws IOException {
        editor.setText(Files.readString(file));
        editor.setCaretPosition(0);
        changed = false;
    }

    /**
     * Returns the file.
     *
     * @return the file.
     */
    @Override
    public Path getFile() {
        return file;
    }

    /**
     * Sets the file and updates the enclosing tab title.
     *
     * @param file the file.
     */
    @Override
    public void setFile(Path file) {
        this.file = file;
        if (SwingUtilities.getAncestorOfClass(JTabbedPane.class, this) instanceof JTabbedPane tabs) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (SwingUtilities.isDescendingFrom(this, tabs.getComponentAt(i))) {
                    tabs.setTitleAt(i, file.getFileName().toString());
                    break;
                }
            }
        }
    }

    /**
     * Returns true if there are no unsaved changes.
     *
     * @return true if there are no unsaved changes.
     */
    @Override
    public boolean isSaved() {
        return !changed;
    }

    /**
     * Closes the autocomplete provider.
     */
    @Override
    public void close() {
        editor.close();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        changed = true;
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changed = true;
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Plain text components like JTextArea don't use attributes,
        // so this is rarely triggered in a simple scenario.
    }
}
