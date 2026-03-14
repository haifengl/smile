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
package smile.llm.tool;

import javax.swing.JOptionPane;

/**
 * The listener of tool calling.
 *
 * @author Haifeng Li
 */
public interface ToolCallListener {
    /**
     * Handles the status update from tool calling.
     * @param status the status update.
     */
    void onStatus(String status);

    /**
     * Display an interactive UI component when agent need to ask the user
     * questions during execution.
     * @param question the question component.
     */
    default void onQuestion(Question question) {
        JOptionPane.showConfirmDialog(
                null,
                question,
                "Agent Question",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE // Message type (removes default icon)
        );
    }
}
