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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.model;

/**
 * The type of Analyst Commands.
 *
 * @author Haifeng Li
 */
public enum CommandType {
    /**
     * Raw, unformatted text that is not evaluated by the analyst.
     */
    Raw("Raw", ""),
    /**
     * Magic commands are special commands that extend the capabilities
     * of Jupyter Notebooks beyond standard Python syntax.
     */
    Magic("Magic", "/"),
    /**
     * Shell commands.
     */
    Shell("Shell", "%"),
    /**
     * Python code.
     */
    Python("Python", "!"),
    /**
     * Text in Markdown format, providing explanations,
     * documentation, or narrative content.
     */
    Markdown("Markdown", "#"),
    /**
     * Instructions in natural language for LLM agents to execute.
     */
    Instructions("Instructions", ">");

    /** The description. */
    private final String description;
    /** The legend. */
    private final String legend;

    /**
     * Constructor.
     * @param description the description.
     * @param legend the legend.
     */
    CommandType(String description, String legend) {
        this.description = description;
        this.legend = legend;
    }

    /**
     * Returns the legend.
     * @return the legend.
     */
    public String legend() {
        return legend;
    }

    @Override
    public String toString() {
        return description;
    }
}
