/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.StringReader;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xml.sax.InputSource;
import smile.plot.swing.Palette;

/**
 * An architect creates model building pipeline.
 *
 * @author Haifeng Li
 */
public class Analyst extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Analyst.class);
    private final JPanel commands = new ScrollablePanel();

    /**
     * Constructor.
     */
    public Analyst() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 8));
        commands.setLayout(new BoxLayout(commands, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(commands);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        Command welcome = new Command(this);
        welcome.setEditable(false);
        welcome.prompt().setText("");
        welcome.input().setText("""
                                                         ..::''''::..
                                                       .;''        ``;.
       ....                                           ::    ::  ::    ::
     ,;' .;:                ()  ..:                  ::     ::  ::     ::
     ::.      ..:,:;.,:;.    .   ::   .::::.         :: .:' ::  :: `:. ::
      '''::,   ::  ::  ::  `::   ::  ;:   .::        ::  :          :  ::
    ,:';  ::;  ::  ::  ::   ::   ::  ::,::''.         :: `:.      .:' ::
    `:,,,,;;' ,;; ,;;, ;;, ,;;, ,;;, `:,,,,:'          `;..``::::''..;'
                                                         ``::,,,,::''
    =====================================================================
    Welcome to Smile Analyst!
    /help for help, /status for your current setup
    cwd: """ + System.getProperty("user.dir"));
        welcome.setInputForeground(Palette.DARK_GRAY);
        welcome.output().setText("""
                Tips for getting started:
                1. Be as specific as you would with another data scientist for the best result
                2. Use SMILE to help with data analysis""");
        commands.add(welcome);

        Command command = new Command(this);
        commands.add(command);
        commands.add(Box.createVerticalGlue());
        SwingUtilities.invokeLater(() -> command.input().requestFocusInWindow());

        Monospace.addListener((e) ->
                SwingUtilities.invokeLater(() -> {
                    Font font = (Font) e.getNewValue();
                    for (int i = 0; i < commands.getComponentCount(); i++) {
                        if (commands.getComponent(i) instanceof Command cmd) {
                            cmd.prompt().setFont(font);
                            cmd.input().setFont(font);
                            cmd.output().setFont(font);
                        }
                    }
                })
        );
    }

    /**
     * Executes command in natural language.
     * @param command the commands to execute.
     */
    public void run(Command command) {

    }

    /**
     * Adds an agent message widget.
     */
    private JComponent addAgentMessage(String message) {
        if (!message.isEmpty()) {
            // Parse Markdown to HTML
            Parser parser = Parser.builder().build();
            Node document = parser.parse(message);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String content = renderer.render(document);

            try {
                String html = """
                        <html>
                        <body style="width: 95%; height: auto; margin: 0 auto;">
                        """ + content + "</body></html>";
                var factory = DocumentBuilderFactory.newInstance();
                var builder = factory.newDocumentBuilder();
                var doc = builder.parse(new InputSource(new StringReader(html)));

                XHTMLPanel browser = new XHTMLPanel();
                browser.setInteractive(false);
                browser.setBackground(getBackground());
                browser.setDocument(doc, null); // The second argument is for base URI, can be null
                return browser;
            } catch (Exception ex) {
                logger.error("Failed to add agent message: ", ex);
                return new JLabel(ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Adds a user message widget.
     */
    private void addUserMessage(String message) {
        if (!message.isEmpty()) {
            JTextArea text = new JTextArea();
            text.setText(message);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setRows(text.getLineCount());

            JPanel pane = new JPanel(new BorderLayout());
            pane.setBorder(new CompoundBorder(
                    new EmptyBorder(8, 8, 8, 16),
                    Command.createRoundBorder()));
            pane.add(text, BorderLayout.CENTER);
            commands.add(pane);

            var response = addAgentMessage(message);
            if (response != null) {
                pane.add(response, BorderLayout.CENTER);
            }
        }
    }

    /**
     * Customized JPanel whose width match the width of its containing
     * JScrollPane's viewport.
     */
    static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel() {

        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ((orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width) - 18;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // This is the key method to make the width match
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false; // Set to true if you also want the height to match
        }
    }
}
