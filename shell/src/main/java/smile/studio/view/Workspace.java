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
import java.io.Serial;

public class Workspace extends JSplitPane {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Explorer explorer = new Explorer();
    private final Notebook notebook = new Notebook();

    public Workspace() {
        super(JSplitPane.HORIZONTAL_SPLIT);
        setLeftComponent(explorer);
        setRightComponent(notebook);
        setDividerLocation(300);
        setPreferredSize(new Dimension(1200, 800));
    }
}
