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
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.rsta.ui.CollapsibleSectionPanel;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.ReplaceToolBar;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.FindToolBar;
import org.fife.ui.rsyntaxtextarea.spell.SpellingParser;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import smile.studio.SmileStudio;
import smile.studio.workspace.OpenFile;

/**
 * A simple text editor. It is hosted as a tab of the workspace tabbed pane.
 *
 * @author Haifeng Li
 */
public final class Notepad extends JPanel implements OpenFile, DocumentListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Notepad.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Notepad.class.getName(), Locale.getDefault());

    /**
     * Display mode for Markdown files.
     */
    public enum Mode {
        /** Raw text/syntax editing mode. */
        EDIT,
        /** Rendered HTML preview mode. */
        PREVIEW
    }

    // TODO: update to lazy constant with Java 25+ (still preview)
    private static SpellingParser dict = null;
    private Path file;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(cardLayout);
    private final CollapsibleSectionPanel csp = new CollapsibleSectionPanel();
    private final JScrollPane previewScroll = new JScrollPane();
    private final JPanel modeBar;
    private final JToggleButton editButton;
    private final JToggleButton previewButton;
    private final Editor editor = new Editor(40, 120);
    /** The search context shared by the inline tool bars. */
    private final SearchContext searchContext = new SearchContext();
    private final FindToolBar findToolBar = new FindToolBar(this);
    private final ReplaceToolBar replaceToolBar = new ReplaceToolBar(this);
    private boolean markdown = false;
    private Mode mode = Mode.EDIT;
    private Markdown markdownView;
    private boolean changed = false;
    /** Notified on every document change, used to schedule a debounced auto save. */
    private Runnable changeListener;

    /**
     * Constructor.
     * @param file the file to open.
     */
    public Notepad(Path file) {
        super(new BorderLayout());
        this.file = file;

        editButton = new JToggleButton(bundle.getString("EditMode"));
        editButton.setToolTipText(bundle.getString("EditModeTooltip"));
        editButton.putClientProperty("JButton.buttonType", "roundRect");
        editButton.setFocusable(false);

        previewButton = new JToggleButton(bundle.getString("PreviewMode"));
        previewButton.setToolTipText(bundle.getString("PreviewModeTooltip"));
        previewButton.putClientProperty("JButton.buttonType", "roundRect");
        previewButton.setFocusable(false);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(editButton);
        modeGroup.add(previewButton);

        editButton.addActionListener(e -> setMode(Mode.EDIT));
        previewButton.addActionListener(e -> setMode(Mode.PREVIEW));

        modeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        modeBar.setOpaque(false);
        modeBar.setBorder(new EmptyBorder(2, 6, 2, 6));
        modeBar.add(editButton);
        modeBar.add(previewButton);

        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);

        contentCards.add(csp, Mode.EDIT.name());
        contentCards.add(previewScroll, Mode.PREVIEW.name());

        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                contentCards.setBounds(0, 0, w, h);
                if (modeBar.isVisible()) {
                    Dimension pref = modeBar.getPreferredSize();
                    int scrollBarWidth = UIManager.getInt("ScrollBar.width");
                    int rightMargin = (scrollBarWidth > 0 ? scrollBarWidth : 14) + 6;
                    int x = Math.max(0, w - pref.width - rightMargin);
                    modeBar.setBounds(x, 6, pref.width, pref.height);
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return contentCards.getPreferredSize();
            }
        };
        layeredPane.add(contentCards, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(modeBar, JLayeredPane.PALETTE_LAYER);
        add(layeredPane, BorderLayout.CENTER);

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
                    File zip = Path.of(System.getProperty("smile.home", "."))
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

        // No ErrorStrip here: pinned to LINE_END it abutted the workspace split
        // divider, doubling the apparent divider width and painting a light
        // caret-marker bar at its top. Its notice threshold (WARNING) is also
        // never met, since the only parser registered is the spell checker,
        // which reports Level.INFO. The scroll pane's gutter already covers
        // markers.

        editor.getDocument().addDocumentListener(this);

        configureMarkdownSupport(Editor.isMarkdown(file), Mode.PREVIEW);
    }

    /**
     * Configures whether this Notepad instance is a Markdown file and sets its initial mode.
     */
    private void configureMarkdownSupport(boolean isMarkdownFile, Mode initialMarkdownMode) {
        this.markdown = isMarkdownFile;
        if (markdown) {
            modeBar.setVisible(true);
            setMode(initialMarkdownMode);
        } else {
            modeBar.setVisible(false);
            setMode(Mode.EDIT);
        }
        revalidate();
        repaint();
    }

    /**
     * Returns true if this editor tab is handling a Markdown file.
     * @return true if Markdown mode switching is enabled for this file.
     */
    public boolean isMarkdown() {
        return markdown;
    }

    /**
     * Returns the current display mode of this editor tab.
     * @return the current {@link Mode}.
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Returns the underlying text editor component.
     * @return the {@link Editor} component.
     */
    public Editor editor() {
        return editor;
    }

    /**
     * Returns the currently rendered {@link Markdown} preview component, or {@code null}
     * if preview has not been rendered yet.
     * @return the {@link Markdown} preview component.
     */
    public Markdown markdownView() {
        return markdownView;
    }

    /**
     * Switches this file tab between {@link Mode#EDIT} and {@link Mode#PREVIEW}.
     * If this file is not a Markdown file, the mode remains {@link Mode#EDIT}.
     *
     * @param newMode the target mode.
     */
    public void setMode(Mode newMode) {
        if (!markdown || newMode == null) {
            newMode = Mode.EDIT;
        }
        this.mode = newMode;
        if (newMode == Mode.PREVIEW) {
            previewButton.setSelected(true);
            refreshPreview();
            cardLayout.show(contentCards, Mode.PREVIEW.name());
        } else {
            editButton.setSelected(true);
            cardLayout.show(contentCards, Mode.EDIT.name());
            editor.requestFocusInWindow();
        }
        revalidate();
        repaint();
    }

    /**
     * Re-renders the Markdown preview from the current editor text buffer.
     */
    private void refreshPreview() {
        markdownView = new Markdown(editor.getText());
        markdownView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    setMode(Mode.EDIT);
                }
            }
        });
        previewScroll.setViewportView(markdownView);
        SwingUtilities.invokeLater(() -> {
            previewScroll.getVerticalScrollBar().setValue(0);
            previewScroll.getHorizontalScrollBar().setValue(0);
        });
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
     * application's Find menu, which routes them to the selected tab.
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
        if (markdown && mode == Mode.PREVIEW) {
            setMode(Mode.EDIT);
        }
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();

        switch (type) {
            case MARK_ALL -> {
                var result = SearchEngine.markAll(editor, context);
                var text = MessageFormat.format(bundle.getString("MarkCount"), result.getMarkedCount());
                SwingUtilities.invokeLater(() -> SmileStudio.setStatus(this, text));
            }
            case FIND -> {
                var result = SearchEngine.find(editor, context);
                if (!result.wasFound() || result.isWrapped()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                } else if (context.getMarkAll()) {
                    var text = MessageFormat.format(bundle.getString("MarkCount"), result.getMarkedCount());
                    SwingUtilities.invokeLater(() -> SmileStudio.setStatus(this, text));
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
            if (markdown && mode == Mode.PREVIEW) {
                setMode(Mode.EDIT);
            }
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
        if (markdown && mode == Mode.PREVIEW) {
            refreshPreview();
        }
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
        var style = Editor.probeSyntaxStyle(file);
        editor.setSyntaxEditingStyle(style);
        boolean nowMarkdown = Editor.isMarkdown(file);
        if (nowMarkdown != this.markdown) {
            configureMarkdownSupport(nowMarkdown, nowMarkdown ? Mode.PREVIEW : Mode.EDIT);
        }
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

    @Override
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
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
        if (changeListener != null) {
            changeListener.run();
        }
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changed = true;
        if (changeListener != null) {
            changeListener.run();
        }
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Plain text components like JTextArea don't use attributes,
        // so this is rarely triggered in a simple scenario.
    }
}
