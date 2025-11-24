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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import smile.plot.swing.Palette;
import smile.studio.model.CommandType;

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
        welcome.setCommandType(CommandType.Raw);
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
        welcome.setEditable(false);
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
                            cmd.indicator().setFont(font);
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
        switch (command.getCommandType()) {
            case Raw -> command.output().setText("Help");
            case Magic -> command.output().setText("Help");
            case Python -> command.output().setText("Help");
            case Markdown -> command.output().setText("Help");
            case Instructions -> command.output().setText("Help");
        }
        command.setEditable(false);

        // Append a new command box.
        Command next = new Command(this);
        commands.add(next, commands.getComponentCount() - 1);
        SwingUtilities.invokeLater(() -> next.input().requestFocusInWindow());
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
