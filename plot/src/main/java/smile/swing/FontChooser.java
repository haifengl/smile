/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>FontChooser</code> class is a swing component 
 * for font selection with <code>JFileChooser</code>-like APIs.
 * The following code pops up a font chooser dialog.
 * <pre>
 * {@code
 *   FontChooser fontChooser = FontChooser.getInstance();
 *   int result = fontChooser.showDialog(parent);
 *   if (result == FontChooser.OK_OPTION) {
 *   	Font font = fontChooser.getSelectedFont(); 
 *   }
 * }
 * </pre>
 */
@SuppressWarnings("serial")
public class FontChooser extends JComponent {
    private static final Logger logger = LoggerFactory.getLogger(FontChooser.class);

	/**
     * Return value from <code>showDialog()</code>.
     * @see #showDialog
     */
    public static final int OK_OPTION = 0;
    /**
     * Return value from <code>showDialog()</code>.
     * @see #showDialog
     */
    public static final int CANCEL_OPTION = 1;
    /**
     * Return value from <code>showDialog()</code>.
     * @see #showDialog
     */
    public static final int ERROR_OPTION = -1;
    private static final Font DEFAULT_SELECTED_FONT = new Font("Serif", Font.PLAIN, 12);
    private static final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 10);
    private static final int[] FONT_STYLE_CODES = {
        Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC
    };
    
    private static final String[] DEFAULT_FONT_SIZE_STRINGS = {
        "8", "9", "10", "11", "12", "14", "16", "18", "20",
        "22", "24", "26", "28", "36", "48", "72",};
    
    // instance variables
    protected int dialogResultValue = ERROR_OPTION;
    private ResourceBundle messageCatalog = ResourceBundle.getBundle(FontChooser.class.getName(), getLocale());

    protected String getMessage(String key) {
        String value = key;
        try {
            value = messageCatalog.getString(key);
        } catch (MissingResourceException e) {
        }
        return value;
    }
    private String[] fontStyleNames = null;
    private String[] fontFamilyNames = null;
    private String[] fontSizeStrings = null;
    private JTextField fontFamilyTextField = null;
    private JTextField fontStyleTextField = null;
    private JTextField fontSizeTextField = null;
    private JList<String> fontNameList = null;
    private JList<String> fontStyleList = null;
    private JList<String> fontSizeList = null;
    private JPanel fontNamePanel = null;
    private JPanel fontStylePanel = null;
    private JPanel fontSizePanel = null;
    private JPanel samplePanel = null;
    private JTextField sampleText = null;

    /**
     * Shared font chooser. An application should have only one font chooser
     * so that it always knows the latest chosen font.
     */
    private static FontChooser chooser = new FontChooser();

    /**
     * Constructs a <code>FontChooser</code> object.
     **/
    public FontChooser() {
        this(DEFAULT_FONT_SIZE_STRINGS);
    }

    /**
     * Constructs a <code>FontChooser</code> object using the given font size array.
     * @param fontSizeStrings  the array of font size string.
     **/
    public FontChooser(String[] fontSizeStrings) {
        if (fontSizeStrings == null) {
            fontSizeStrings = DEFAULT_FONT_SIZE_STRINGS;
        }
        this.fontSizeStrings = fontSizeStrings;

        JPanel selectPanel = new JPanel();
        selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.X_AXIS));
        selectPanel.add(getFontFamilyPanel());
        selectPanel.add(getFontStylePanel());
        selectPanel.add(getFontSizePanel());

        JPanel contentsPanel = new JPanel();
        contentsPanel.setLayout(new GridLayout(2, 1));
        contentsPanel.add(selectPanel, BorderLayout.NORTH);
        contentsPanel.add(getSamplePanel(), BorderLayout.CENTER);

        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.add(contentsPanel);
        this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        this.setSelectedFont(DEFAULT_SELECTED_FONT);
    }

    /**
     * Returns the shared font chooser instance. In general, an application
     * should have only one font chooser so that it always knows the latest
     * chosen font.
     */
    public static FontChooser getInstance() {
        return chooser;
    }
    
    private JTextField getFontFamilyTextField() {
        if (fontFamilyTextField == null) {
            fontFamilyTextField = new JTextField();
            fontFamilyTextField.addFocusListener(
                    new TextFieldFocusHandlerForTextSelection(fontFamilyTextField));
            fontFamilyTextField.addKeyListener(
                    new TextFieldKeyHandlerForListSelectionUpDown(getFontFamilyList()));
            fontFamilyTextField.getDocument().addDocumentListener(
                    new ListSearchTextFieldDocumentHandler(getFontFamilyList()));
            fontFamilyTextField.setFont(DEFAULT_FONT);

        }
        return fontFamilyTextField;
    }

    private JTextField getFontStyleTextField() {
        if (fontStyleTextField == null) {
            fontStyleTextField = new JTextField();
            fontStyleTextField.addFocusListener(
                    new TextFieldFocusHandlerForTextSelection(fontStyleTextField));
            fontStyleTextField.addKeyListener(
                    new TextFieldKeyHandlerForListSelectionUpDown(getFontStyleList()));
            fontStyleTextField.getDocument().addDocumentListener(
                    new ListSearchTextFieldDocumentHandler(getFontStyleList()));
            fontStyleTextField.setFont(DEFAULT_FONT);
        }
        return fontStyleTextField;
    }

    private JTextField getFontSizeTextField() {
        if (fontSizeTextField == null) {
            fontSizeTextField = new JTextField();
            fontSizeTextField.addFocusListener(
                    new TextFieldFocusHandlerForTextSelection(fontSizeTextField));
            fontSizeTextField.addKeyListener(
                    new TextFieldKeyHandlerForListSelectionUpDown(getFontSizeList()));
            fontSizeTextField.getDocument().addDocumentListener(
                    new ListSearchTextFieldDocumentHandler(getFontSizeList()));
            fontSizeTextField.setFont(DEFAULT_FONT);
        }
        return fontSizeTextField;
    }

    private JList<String> getFontFamilyList() {
        if (fontNameList == null) {
            fontNameList = new JList<>(getFontFamilies());
            fontNameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            fontNameList.addListSelectionListener(
                    new ListSelectionHandler(getFontFamilyTextField()));
            fontNameList.setSelectedIndex(0);
            fontNameList.setFont(DEFAULT_FONT);
            fontNameList.setFocusable(false);
        }
        return fontNameList;
    }

    private JList<String> getFontStyleList() {
        if (fontStyleList == null) {
            fontStyleList = new JList<>(getFontStyleNames());
            fontStyleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            fontStyleList.addListSelectionListener(
                    new ListSelectionHandler(getFontStyleTextField()));
            fontStyleList.setSelectedIndex(0);
            fontStyleList.setFont(DEFAULT_FONT);
            fontStyleList.setFocusable(false);
        }
        return fontStyleList;
    }

    private JList<String> getFontSizeList() {
        if (fontSizeList == null) {
            fontSizeList = new JList<>(this.fontSizeStrings);
            fontSizeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            fontSizeList.addListSelectionListener(
                    new ListSelectionHandler(getFontSizeTextField()));
            fontSizeList.setSelectedIndex(0);
            fontSizeList.setFont(DEFAULT_FONT);
            fontSizeList.setFocusable(false);
        }
        return fontSizeList;
    }

    /**
     * Get the family name of the selected font.
     * @return  the font family of the selected font.
     *
     * @see #setSelectedFontFamily
     **/
    public String getSelectedFontFamily() {
        String fontName = (String) getFontFamilyList().getSelectedValue();
        return fontName;
    }

    /**
     * Get the style of the selected font.
     * @return  the style of the selected font.
     *          <code>Font.PLAIN</code>, <code>Font.BOLD</code>,
     *          <code>Font.ITALIC</code>, <code>Font.BOLD|Font.ITALIC</code>
     *
     * @see java.awt.Font#PLAIN
     * @see java.awt.Font#BOLD
     * @see java.awt.Font#ITALIC
     * @see #setSelectedFontStyle
     **/
    public int getSelectedFontStyle() {
        int index = getFontStyleList().getSelectedIndex();
        return FONT_STYLE_CODES[index];
    }

    /**
     * Get the size of the selected font.
     * @return  the size of the selected font
     *
     * @see #setSelectedFontSize
     **/
    public int getSelectedFontSize() {
        int fontSize = 1;
        String fontSizeString = getFontSizeTextField().getText();
        while (true) {
            try {
                fontSize = Integer.parseInt(fontSizeString);
                break;
            } catch (NumberFormatException e) {
                fontSizeString = (String) getFontSizeList().getSelectedValue();
                getFontSizeTextField().setText(fontSizeString);
            }
        }

        return fontSize;
    }

    /**
     * Get the selected font.
     * @return  the selected font
     *
     * @see #setSelectedFont
     * @see java.awt.Font
     **/
    public Font getSelectedFont() {
        Font font = new Font(getSelectedFontFamily(),
                getSelectedFontStyle(), getSelectedFontSize());
        return font;
    }

    /**
     * Set the family name of the selected font.
     * @param name  the family name of the selected font. 
     *
     * @see #getSelectedFontFamily
     **/
    public FontChooser setSelectedFontFamily(String name) {
        String[] names = getFontFamilies();
        for (int i = 0; i < names.length; i++) {
            if (names[i].toLowerCase().equals(name.toLowerCase())) {
                getFontFamilyList().setSelectedIndex(i);
                break;
            }
        }
        updateSampleFont();
        return this;
    }

    /**
     * Set the style of the selected font.
     * @param style  the size of the selected font.
     *               <code>Font.PLAIN</code>, <code>Font.BOLD</code>,
     *               <code>Font.ITALIC</code>, or
     *               <code>Font.BOLD|Font.ITALIC</code>.
     *
     * @see java.awt.Font#PLAIN
     * @see java.awt.Font#BOLD
     * @see java.awt.Font#ITALIC
     * @see #getSelectedFontStyle
     **/
    public FontChooser setSelectedFontStyle(int style) {
        for (int i = 0; i < FONT_STYLE_CODES.length; i++) {
            if (FONT_STYLE_CODES[i] == style) {
                getFontStyleList().setSelectedIndex(i);
                break;
            }
        }
        updateSampleFont();
        return this;
    }

    /**
     * Set the size of the selected font.
     * @param size the size of the selected font
     *
     * @see #getSelectedFontSize
     **/
    public FontChooser setSelectedFontSize(int size) {
        String sizeString = String.valueOf(size);
        for (int i = 0; i < this.fontSizeStrings.length; i++) {
            if (this.fontSizeStrings[i].equals(sizeString)) {
                getFontSizeList().setSelectedIndex(i);
                break;
            }
        }
        getFontSizeTextField().setText(sizeString);
        updateSampleFont();
        return this;
    }

    /**
     * Set the selected font.
     * @param font the selected font
     *
     * @see #getSelectedFont
     * @see java.awt.Font
     **/
    public FontChooser setSelectedFont(Font font) {
        setSelectedFontFamily(font.getFamily());
        setSelectedFontStyle(font.getStyle());
        setSelectedFontSize(font.getSize());
        return this;
    }

    /**
     *  Show font selection dialog.
     *  @param parent Dialog's Parent component.
     *  @return OK_OPTION, CANCEL_OPTION or ERROR_OPTION
     *
     *  @see #OK_OPTION 
     *  @see #CANCEL_OPTION
     *  @see #ERROR_OPTION
     **/
    public int showDialog(Component parent) {
        dialogResultValue = ERROR_OPTION;
        JDialog dialog = createDialog(parent);
        dialog.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                dialogResultValue = CANCEL_OPTION;
            }
        });

        dialog.setVisible(true);
        dialog.dispose();
        dialog = null;

        return dialogResultValue;
    }

    class ListSelectionHandler implements ListSelectionListener {

        private JTextComponent textComponent;

        ListSelectionHandler(JTextComponent textComponent) {
            this.textComponent = textComponent;
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting() == false) {
                @SuppressWarnings("unchecked")
                JList<String> list = (JList<String>) e.getSource();
                String selectedValue = (String) list.getSelectedValue();

                String oldValue = textComponent.getText();
                textComponent.setText(selectedValue);
                if (!oldValue.equalsIgnoreCase(selectedValue)) {
                    textComponent.selectAll();
                    textComponent.requestFocus();
                }

                updateSampleFont();
            }
        }
    }

    class TextFieldFocusHandlerForTextSelection extends FocusAdapter {

        private JTextComponent textComponent;

        public TextFieldFocusHandlerForTextSelection(JTextComponent textComponent) {
            this.textComponent = textComponent;
        }

        @Override
        public void focusGained(FocusEvent e) {
            textComponent.selectAll();
        }

        @Override
        public void focusLost(FocusEvent e) {
            textComponent.select(0, 0);
            updateSampleFont();
        }
    }

    class TextFieldKeyHandlerForListSelectionUpDown extends KeyAdapter {

        private JList<String> targetList;

        public TextFieldKeyHandlerForListSelectionUpDown(JList<String> list) {
            this.targetList = list;
        }

        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_UP:
                    int up = targetList.getSelectedIndex() - 1;
                    if (up < 0) {
                        up = 0;
                    }
                    targetList.setSelectedIndex(up);
                    break;
                case KeyEvent.VK_DOWN:
                    int listSize = targetList.getModel().getSize();
                    int down = targetList.getSelectedIndex() + 1;
                    if (down >= listSize) {
                        down = listSize - 1;
                    }
                    targetList.setSelectedIndex(down);
                    break;
                default:
                    break;
            }
        }
    }

    class ListSearchTextFieldDocumentHandler implements DocumentListener {

        JList<String> targetList;

        public ListSearchTextFieldDocumentHandler(JList<String> targetList) {
            this.targetList = targetList;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            update(e);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            update(e);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            update(e);
        }

        private void update(DocumentEvent event) {
            String newValue = "";
            try {
                Document doc = event.getDocument();
                newValue = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                logger.error("update(DocumentEvent) exception", ex);
            }

            if (!newValue.isEmpty()) {
                int index = targetList.getNextMatch(newValue, 0, Position.Bias.Forward);
                if (index < 0) {
                    index = 0;
                }
                targetList.ensureIndexIsVisible(index);

                String matchedName = targetList.getModel().getElementAt(index).toString();
                if (newValue.equalsIgnoreCase(matchedName)) {
                    if (index != targetList.getSelectedIndex()) {
                        SwingUtilities.invokeLater(new ListSelector(index));
                    }
                }
            }
        }

        public class ListSelector implements Runnable {

            private int index;

            public ListSelector(int index) {
                this.index = index;
            }

            @Override
            public void run() {
                targetList.setSelectedIndex(this.index);
            }
        }
    }

    class DialogOKAction extends AbstractAction {

        protected static final String ACTION_NAME = "OK";
        private JDialog dialog;

        protected DialogOKAction(JDialog dialog) {
            this.dialog = dialog;
            putValue(Action.DEFAULT, ACTION_NAME);
            putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
            putValue(Action.NAME, getMessage(ACTION_NAME));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dialogResultValue = OK_OPTION;
            dialog.setVisible(false);
        }
    }

    class DialogCancelAction extends AbstractAction {

        protected static final String ACTION_NAME = "Cancel";
        private JDialog dialog;

        protected DialogCancelAction(JDialog dialog) {
            this.dialog = dialog;
            putValue(Action.DEFAULT, ACTION_NAME);
            putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
            putValue(Action.NAME, getMessage(ACTION_NAME));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dialogResultValue = CANCEL_OPTION;
            dialog.setVisible(false);
        }
    }

    private JDialog createDialog(Component parent) {
        Frame frame = parent instanceof Frame ? (Frame) parent
                : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
        JDialog dialog = new JDialog(frame, getMessage("SelectFont"), true);

        Action okAction = new DialogOKAction(dialog);
        Action cancelAction = new DialogCancelAction(dialog);

        JButton okButton = new JButton(okAction);
        okButton.setFont(DEFAULT_FONT);
        JButton cancelButton = new JButton(cancelAction);
        cancelButton.setFont(DEFAULT_FONT);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new GridLayout(2, 1));
        buttonsPanel.add(okButton);
        buttonsPanel.add(cancelButton);
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(25, 0, 10, 10));

        ActionMap actionMap = buttonsPanel.getActionMap();
        actionMap.put(cancelAction.getValue(Action.DEFAULT), cancelAction);
        actionMap.put(okAction.getValue(Action.DEFAULT), okAction);
        InputMap inputMap = buttonsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), cancelAction.getValue(Action.DEFAULT));
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), okAction.getValue(Action.DEFAULT));

        JPanel dialogEastPanel = new JPanel();
        dialogEastPanel.setLayout(new BorderLayout());
        dialogEastPanel.add(buttonsPanel, BorderLayout.NORTH);

        dialog.getContentPane().add(this, BorderLayout.CENTER);
        dialog.getContentPane().add(dialogEastPanel, BorderLayout.EAST);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        return dialog;
    }

    private void updateSampleFont() {
        Font font = getSelectedFont();
        getSampleTextField().setFont(font);
    }

    private JPanel getFontFamilyPanel() {
        if (fontNamePanel == null) {
            fontNamePanel = new JPanel();
            fontNamePanel.setLayout(new BorderLayout());
            fontNamePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            fontNamePanel.setPreferredSize(new Dimension(180, 130));

            JScrollPane scrollPane = new JScrollPane(getFontFamilyList());
            scrollPane.getVerticalScrollBar().setFocusable(false);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

            JPanel p = new JPanel();
            p.setLayout(new BorderLayout());
            p.add(getFontFamilyTextField(), BorderLayout.NORTH);
            p.add(scrollPane, BorderLayout.CENTER);

            JLabel label = new JLabel(getMessage("FontName"));
            label.setHorizontalAlignment(JLabel.LEFT);
            label.setHorizontalTextPosition(JLabel.LEFT);
            label.setLabelFor(getFontFamilyTextField());
            label.setDisplayedMnemonic('F');

            fontNamePanel.add(label, BorderLayout.NORTH);
            fontNamePanel.add(p, BorderLayout.CENTER);

        }
        return fontNamePanel;
    }

    private JPanel getFontStylePanel() {
        if (fontStylePanel == null) {
            fontStylePanel = new JPanel();
            fontStylePanel.setLayout(new BorderLayout());
            fontStylePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            fontStylePanel.setPreferredSize(new Dimension(140, 130));

            JScrollPane scrollPane = new JScrollPane(getFontStyleList());
            scrollPane.getVerticalScrollBar().setFocusable(false);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

            JPanel p = new JPanel();
            p.setLayout(new BorderLayout());
            p.add(getFontStyleTextField(), BorderLayout.NORTH);
            p.add(scrollPane, BorderLayout.CENTER);

            JLabel label = new JLabel(getMessage("FontStyle"));
            label.setHorizontalAlignment(JLabel.LEFT);
            label.setHorizontalTextPosition(JLabel.LEFT);
            label.setLabelFor(getFontStyleTextField());
            label.setDisplayedMnemonic('Y');

            fontStylePanel.add(label, BorderLayout.NORTH);
            fontStylePanel.add(p, BorderLayout.CENTER);
        }
        return fontStylePanel;
    }

    private JPanel getFontSizePanel() {
        if (fontSizePanel == null) {
            fontSizePanel = new JPanel();
            fontSizePanel.setLayout(new BorderLayout());
            fontSizePanel.setPreferredSize(new Dimension(70, 130));
            fontSizePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JScrollPane scrollPane = new JScrollPane(getFontSizeList());
            scrollPane.getVerticalScrollBar().setFocusable(false);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

            JPanel p = new JPanel();
            p.setLayout(new BorderLayout());
            p.add(getFontSizeTextField(), BorderLayout.NORTH);
            p.add(scrollPane, BorderLayout.CENTER);

            JLabel label = new JLabel(getMessage("FontSize"));
            label.setHorizontalAlignment(JLabel.LEFT);
            label.setHorizontalTextPosition(JLabel.LEFT);
            label.setLabelFor(getFontSizeTextField());
            label.setDisplayedMnemonic('S');

            fontSizePanel.add(label, BorderLayout.NORTH);
            fontSizePanel.add(p, BorderLayout.CENTER);
        }
        return fontSizePanel;
    }

    private JPanel getSamplePanel() {
        if (samplePanel == null) {
            Border titledBorder = BorderFactory.createTitledBorder(
                    BorderFactory.createEtchedBorder(), getMessage("Sample"));
            Border empty = BorderFactory.createEmptyBorder(5, 10, 10, 10);
            Border border = BorderFactory.createCompoundBorder(titledBorder, empty);

            samplePanel = new JPanel();
            samplePanel.setLayout(new BorderLayout());
            samplePanel.setBorder(border);

            samplePanel.add(getSampleTextField(), BorderLayout.CENTER);
        }
        return samplePanel;
    }

    private JTextField getSampleTextField() {
        if (sampleText == null) {
            Border lowered = BorderFactory.createLoweredBevelBorder();

            sampleText = new JTextField(getMessage("SampleString"));
            sampleText.setBorder(lowered);
            sampleText.setPreferredSize(new Dimension(300, 100));
        }
        return sampleText;
    }

    private String[] getFontFamilies() {
        if (fontFamilyNames == null) {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            fontFamilyNames = env.getAvailableFontFamilyNames();
        }
        return fontFamilyNames;
    }

    private String[] getFontStyleNames() {
        if (fontStyleNames == null) {
            int i = 0;
            fontStyleNames = new String[4];
            fontStyleNames[i++] = getMessage("Plain");
            fontStyleNames[i++] = getMessage("Bold");
            fontStyleNames[i++] = getMessage("Italic");
            fontStyleNames[i++] = getMessage("BoldItalic");
        }
        return fontStyleNames;
    }
}
