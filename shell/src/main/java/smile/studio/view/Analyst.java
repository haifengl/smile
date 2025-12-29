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
import smile.shell.JShell;
import smile.studio.kernel.JavaRunner;
import smile.studio.model.CommandType;
import smile.swing.ScrollablePanel;

/**
 * An architect creates model building pipeline.
 *
 * @author Haifeng Li
 */
public class Analyst extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Analyst.class);
    private final JPanel commands = new ScrollablePanel();
    /** JShell instance. */
    private final JavaRunner runner;

    /**
     * Constructor.
     * @param runner Java code execution engine.
     */
    public Analyst(JavaRunner runner) {
        super(new BorderLayout());
        this.runner = runner;

        setBorder(new EmptyBorder(0, 0, 0, 8));
        commands.setLayout(new BoxLayout(commands, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(commands);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        commands.add(welcome());
        commands.add(new Command(this));
        commands.add(Box.createVerticalGlue());

        Monospaced.addListener((e) ->
                SwingUtilities.invokeLater(() -> {
                    Font font = (Font) e.getNewValue();
                    for (int i = 0; i < commands.getComponentCount(); i++) {
                        if (commands.getComponent(i) instanceof Command cmd) {
                            cmd.indicator().setFont(font);
                            cmd.editor().setFont(font);
                            cmd.output().setFont(font);
                        }
                    }
                })
        );
    }

    /** Returns the welcome banner. */
    private Command welcome() {
        Command welcome = new Command(this);
        welcome.setCommandType(CommandType.Raw);
        welcome.setEditable(false);
        welcome.setInputForeground(Palette.DARK_GRAY);
        welcome.editor().setText(JShell.logo.replaceAll("(?m)^\\s{3}", "") + """
    =====================================================================
    Welcome to Smile Analyst!
    
    /help for help, /init for initializing your project
    cwd:\s""" + System.getProperty("user.dir"));
        welcome.output().setText("""
    As a state-of-the-art machine learning engineering agent,
    Smile Analyst can help you with:
    
    🤖 Automatic end-to-end ML/AI solutions based on your requirements.
    🔍 Best practices and state-of-the-art methods with web search.
    🏅 Targeted code block refinement by ablation study.
    🤝 Improved solution using iterative ensemble strategy.
    💡 High-quality code completion and generation.
    📊 Advanced interactive data visualization.
    📂 Process data from CSV, ARFF, JSON, Avro, Parquet, Iceberg, to SQL.
    🌐 Built-in inference server.
    
    Tips for getting started:
    1. Run /init to create a SMILE.md file with instructions for agent.
    2. Be as specific as you would with another data scientist for the best result.
    3. Use magic and shell commands to help with data analysis, git, etc.
    4. Data visualization can be feed to AI agents for interpretation and advices.
    5. Python and Markdown are supported too.
    6. AI can make mistakes. Always review agent's responses.""");
        return welcome;
    }

    /**
     * Executes command in natural language.
     * @param command the commands to execute.
     */
    public void run(Command command) {
        if (command.getCommandType() == CommandType.Instructions) {
            command.output().setText(command.editor().getText());
        }
    }

    /** Append a new command box. */
    public void addCommand() {
        Command command = new Command(this);
        commands.add(command, commands.getComponentCount() - 1);
        SwingUtilities.invokeLater(() -> command.editor().requestFocusInWindow());
    }
}
