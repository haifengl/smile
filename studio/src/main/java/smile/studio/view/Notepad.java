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
package smile.studio.view;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.fife.rsta.ui.CollapsibleSectionPanel;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.ReplaceToolBar;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.rsta.ui.search.FindToolBar;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

/**
 * A simple text editor.
 *
 * @author Haifeng Li
 */
public final class Notepad extends JFrame implements SearchListener {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Notepad.class.getName(), Locale.getDefault());

    private final Path file;
    private final CollapsibleSectionPanel csp = new CollapsibleSectionPanel();
    private final RSyntaxTextArea editor = new CodeEditor(40, 120);
    private final StatusBar statusBar = new StatusBar();
    private final FindDialog findDialog = new FindDialog(this, this);
    private final ReplaceDialog replaceDialog = new ReplaceDialog(this, this);
    private final FindToolBar findToolBar = new FindToolBar(this);
    private final ReplaceToolBar replaceToolBar = new ReplaceToolBar(this);

    /**
     * Constructor.
     * @param file the file to open.
     */
    private Notepad(Path file) {
        this.file = file;
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);
        contentPane.add(csp, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
        setJMenuBar(createMenuBar());
        initSearchDialogs();

        editor.setFont(Monospaced.getFont());
        editor.setCodeFoldingEnabled(true);
        editor.setMarkOccurrences(true);

        try {
            String content = Files.readString(file);
            editor.setText(content);
            editor.setCaretPosition(0);

            switch (Files.probeContentType(file)) {
                case "text/markdown":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
                    break;
                case "text/x-java-source":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
                    break;
                case "text/x-python":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                    break;
                case "text/x-scala":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SCALA);
                    break;
                case "text/x-c++src":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
                    break;
                case "text/x-csrc":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
                    break;
                case "text/x-javascript":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
                    break;
                case "text/x-rustsrc":
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUST);
                    break;
                default:
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            );
        }
        RTextScrollPane sp = new RTextScrollPane(editor);
        csp.add(sp);

        ErrorStrip errorStrip = new ErrorStrip(editor);
        contentPane.add(errorStrip, BorderLayout.LINE_END);

        setTitle(file.normalize().toAbsolutePath().toString());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    private void addMenuItem(Action action, ButtonGroup group, JMenu menu) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(action);
        group.add(item);
        menu.add(item);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();
        JMenu fileMenu = new JMenu(bundle.getString("File"));
        fileMenu.add(new JMenuItem(new SaveFileAction()));
        fileMenu.add(new JMenuItem(new ExitAction()));
        menubar.add(fileMenu);

        JMenu searchMenu = new JMenu(bundle.getString("Search"));
        searchMenu.add(new JMenuItem(new ShowFindDialogAction()));
        searchMenu.add(new JMenuItem(new ShowReplaceDialogAction()));
        searchMenu.add(new JMenuItem(new GoToLineAction()));
        searchMenu.addSeparator();

        int ctrl = getToolkit().getMenuShortcutKeyMaskEx();
        int shift = InputEvent.SHIFT_DOWN_MASK;
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrl|shift);
        Action action = csp.addBottomComponent(key, findToolBar);
        action.putValue(Action.NAME, bundle.getString("ShowFindBar"));
        searchMenu.add(new JMenuItem(action));
        key = KeyStroke.getKeyStroke(KeyEvent.VK_H, ctrl|shift);
        action = csp.addBottomComponent(key, replaceToolBar);
        action.putValue(Action.NAME, bundle.getString("ShowReplaceBar"));
        searchMenu.add(new JMenuItem(action));

        menubar.add(searchMenu);
        return menubar;
    }

    @Override
    public String getSelectedText() {
        return editor.getSelectedText();
    }

    /**
     * Creates our Find and Replace dialogs.
     */
    private void initSearchDialogs() {
        // This ties the properties of the two dialogs together (match case, regex, etc.).
        SearchContext context = findDialog.getSearchContext();
        replaceDialog.setSearchContext(context);

        // Tie toolbar's search contexts together.
        findToolBar.setSearchContext(context);
        replaceToolBar.setSearchContext(context);
    }

    /**
     * Listens for events from our search dialogs and actually does the work.
     */
    @Override
    public void searchEvent(SearchEvent e) {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();
        SearchResult result;

        switch (type) {
            case MARK_ALL:
                result = SearchEngine.markAll(editor, context);
                break;
            case FIND:
                result = SearchEngine.find(editor, context);
                if (!result.wasFound() || result.isWrapped()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                }
                break;
            case REPLACE:
                result = SearchEngine.replace(editor, context);
                if (!result.wasFound() || result.isWrapped()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                }
                break;
            case REPLACE_ALL:
                result = SearchEngine.replaceAll(editor, context);
                JOptionPane.showMessageDialog(
                        null,
                        result.getCount() + " occurrences replaced.");
                break;
            default:
                return;
        }

        String text;
        if (result.wasFound()) {
            text = "Text found; occurrences marked: " + result.getMarkedCount();
        } else if (type == SearchEvent.Type.MARK_ALL) {
            text = result.getMarkedCount() <= 0 ? "" : "Occurrences marked: " + result.getMarkedCount();
        } else {
            text = "Text not found";
        }
        statusBar.setStatus(text);
    }

    /**
     * Opens a file with notepad.
     * @param file the file to open.
     */
    public static void open(Path file) {
        SwingUtilities.invokeLater(() -> new Notepad(file).setVisible(true));
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
            if (findDialog.isVisible()) {
                findDialog.setVisible(false);
            }
            if (replaceDialog.isVisible()) {
                replaceDialog.setVisible(false);
            }
            GoToDialog dialog = new GoToDialog(Notepad.this);
            dialog.setMaxLineNumberAllowed(editor.getLineCount());
            dialog.setVisible(true);
            int line = dialog.getLineNumber();
            if (line > 0) {
                try {
                    editor.setCaretPosition(editor.getLineStartOffset(line-1));
                } catch (BadLocationException ex) { // Never happens
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                    System.err.println("Error: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Shows the Find dialog.
     */
    private class ShowFindDialogAction extends AbstractAction {
        ShowFindDialogAction() {
            super(bundle.getString("Find"));
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (replaceDialog.isVisible()) {
                replaceDialog.setVisible(false);
            }
            findDialog.setVisible(true);
        }

    }

    /**
     * Shows the Replace dialog.
     */
    private class ShowReplaceDialogAction extends AbstractAction {
        ShowReplaceDialogAction() {
            super(bundle.getString("Replace"));
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_H, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (findDialog.isVisible()) {
                findDialog.setVisible(false);
            }
            replaceDialog.setVisible(true);
        }

    }

    private class SaveFileAction extends AbstractAction {
        public SaveFileAction() {
            super(bundle.getString("Save"));
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                Files.writeString(file, editor.getText());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        Notepad.this,
                        ex.getMessage(),
                        bundle.getString("Error"),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private class ExitAction extends AbstractAction {
        public ExitAction() {
            super(bundle.getString("Exit"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var notepad = Notepad.this;
            dispatchEvent(new WindowEvent(notepad, WindowEvent.WINDOW_CLOSING));
        }
    }
}
