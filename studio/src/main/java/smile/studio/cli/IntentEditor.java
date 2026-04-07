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
package smile.studio.cli;

import smile.studio.Monospaced;
import smile.studio.text.Editor;

/**
 * Agent CLI intent editor.
 *
 * @author Haifeng Li
 */
public class IntentEditor extends Editor {
    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     */
    public IntentEditor(int rows, int cols) {
        super(rows, cols);
        setFont(Monospaced.getFont());
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setHighlightCurrentLine(false);
    }
}
