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
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.fife.rsta.ui.CollapsibleSectionPanel;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

/**
 * A text area with search and replace toolbar.
 *
 * @author Haifeng Li
 */
public final class SearchableTextArea extends CollapsibleSectionPanel implements SearchListener {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SearchableTextArea.class.getName(), Locale.getDefault());

    private final RSyntaxTextArea editor;
    private final RTextScrollPane scrollPane;
    private final FindDialog findDialog;
    private final ReplaceDialog replaceDialog;
    private final FindToolBar findToolBar = new FindToolBar(this);
    private final ReplaceToolBar replaceToolBar = new ReplaceToolBar(this);

    /**
     * Constructor.
     * @param editor the editor instance.
     */
    private SearchableTextArea(RSyntaxTextArea editor) {
        this(null, editor);
    }

    /**
     * Constructor.
     * @param editor the editor instance.
     */
    private SearchableTextArea(Frame owner, RSyntaxTextArea editor) {
        this.editor = editor;
        this.findDialog = new FindDialog(owner, this);
        this.replaceDialog = new ReplaceDialog(owner, this);

        scrollPane = new RTextScrollPane(editor);
        add(scrollPane);
        initSearchDialogs();
    }

    /**
     * Returns the scroll pane of editor.
     * @return the scroll pane of editor.
     */
    public RTextScrollPane scrollPane() {
        return scrollPane;
    }

    /**
     * Creates the search menu.
     * @return the search menu.
     */
    public JMenu createSearchMenu() {
        JMenu searchMenu = new JMenu(bundle.getString("Search"));
        searchMenu.add(new JMenuItem(new ShowFindDialogAction()));
        Action showReplaceDialogAction = new ShowReplaceDialogAction();
        showReplaceDialogAction.setEnabled(editor.isEditable());
        searchMenu.add(new JMenuItem(showReplaceDialogAction));
        searchMenu.add(new JMenuItem(new GoToLineAction()));
        searchMenu.addSeparator();

        int ctrl = getToolkit().getMenuShortcutKeyMaskEx();
        int shift = InputEvent.SHIFT_DOWN_MASK;
        KeyStroke ctrlShiftF = KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrl|shift);
        Action showFindBarAction = addBottomComponent(ctrlShiftF, findToolBar);
        showFindBarAction.putValue(Action.NAME, bundle.getString("ShowFindBar"));
        searchMenu.add(new JMenuItem(showFindBarAction));

        KeyStroke ctrlShiftH = KeyStroke.getKeyStroke(KeyEvent.VK_H, ctrl|shift);
        Action showReplaceBarAction = addBottomComponent(ctrlShiftH, replaceToolBar);
        showReplaceBarAction.putValue(Action.NAME, bundle.getString("ShowReplaceBar"));
        showReplaceBarAction.setEnabled(editor.isEditable());
        searchMenu.add(new JMenuItem(showReplaceBarAction));
        return searchMenu;
    }

    /**
     * Displays the find toolbar at the bottom of pane.
     */
    public void showFindBar() {
        showBottomComponent(findToolBar);
    }

    /**
     * Displays the replace toolbar at the bottom of pane.
     */
    public void showReplaceBar() {
        showBottomComponent(replaceToolBar);
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
                        this,
                        result.getCount() + " occurrences replaced.");
                break;
            default:
                return;
        }

        if (!result.wasFound() && type != SearchEvent.Type.MARK_ALL) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            bundle.getString("TextNotFound"),
                            bundle.getString("Search"),
                            JOptionPane.INFORMATION_MESSAGE
                    ));
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
            if (findDialog.isVisible()) {
                findDialog.setVisible(false);
            }
            if (replaceDialog.isVisible()) {
                replaceDialog.setVisible(false);
            }
            GoToDialog dialog = new GoToDialog((Frame) SwingUtilities.windowForComponent(editor));
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
}
