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
import java.awt.*;

/**
 * A cell is a multiline text input field, and its contents can be executed.
 * The execution behavior of a cell is determined by the cell's type.
 * There are three types of cells: code cells, markdown cells, and raw cells.
 *
 * @author Haifeng Li
 */
public class Cell extends JPanel {
    CodeEditor editor;
    JPanel result;

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        editor.setFont(font);
        result.setFont(font);
    }
}
