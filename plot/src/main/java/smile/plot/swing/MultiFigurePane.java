/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.*;
import javax.swing.*;
import smile.data.DataFrame;
import smile.swing.Button;

/**
 * Interactive view for multiple mathematical plots.
 *
 * @author Haifeng Li
 */
public class MultiFigurePane extends JPanel implements Scene {
    /**
     * The toolbar to control plots.
     */
    private final JToolBar toolbar;
    /**
     * The content pane.
     */
    private final JPanel content;

    /**
     * Constructor.
     * @param layout the layout manager.
     */
    public MultiFigurePane(LayoutManager layout) {
        super(layout, true);
        toolbar = new JToolBar();
        toolbar.add(new Button(saveAction()));
        toolbar.add(new Button(printAction()));
        add(toolbar, BorderLayout.NORTH);

        content = new JPanel(layout);
        content.setBackground(Color.WHITE);
        add(content, BorderLayout.CENTER);
    }

    /**
     * Constructor with GridLayout.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    public MultiFigurePane(int nrow, int ncol) {
        this(grid(nrow, ncol));
    }

    /**
     * Constructor with GridLayout.
     * @param plots the plots to add into the frame.
     */
    public MultiFigurePane(Canvas... plots) {
        this(grid(plots.length));
        for (var plot : plots) {
            add(plot);
        }
    }

    /**
     * Returns a grid layout manager.
     * @param size the number of plots.
     */
    private static LayoutManager grid(int size) {
        int n = (int) Math.ceil(Math.sqrt(size));
        if (n < 1) n = 1;
        return grid(n, n);
    }

    /**
     * Returns a grid layout manager.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    private static LayoutManager grid(int nrow, int ncol) {
        return new GridLayout(nrow, ncol, 0, 0);
    }

    @Override
    public JComponent content() {
        return content;
    }

    @Override
    public JToolBar toolbar() {
        return toolbar;
    }

    /**
     * Returns the scatter plot matrix (SPLOM).
     * @param data the data frame.
     * @param mark the mark of points.
     * @param color the color of points.
     * @return the scatter plot matrix.
     */
    public static MultiFigurePane splom(DataFrame data, char mark, Color color) {
        String[] columns = data.names();
        int p = columns.length;
        MultiFigurePane grid = new MultiFigurePane(p, p);
        for (int i = p; i-- > 0;) {
            for (String column : columns) {
                Figure figure = ScatterPlot.of(data, column, columns[i], mark, color).figure();
                figure.setAxisLabels(column, columns[i]);
                grid.content().add(new Canvas(figure));
            }
        }

        return grid;
    }

    /**
     * Returns the scatter plot matrix (SPLOM).
     * @param data the data frame.
     * @param mark the mark of points.
     * @param category the category column for coloring.
     * @return the scatter plot matrix.
     */
    public static MultiFigurePane splom(DataFrame data, char mark, String category) {
        int clazz = data.schema().indexOf(category);
        String[] columns = data.names();
        int p = columns.length;
        MultiFigurePane grid = new MultiFigurePane(p, p);
        for (int i = p; i-- > 0;) {
            if (i == clazz) continue;
            for (int j = 0; j < p; j++) {
                if (j == clazz) continue;
                Figure figure = ScatterPlot.of(data, columns[j], columns[i], category, mark).figure();
                figure.setLegendVisible(false);
                figure.setAxisLabels(columns[j], columns[i]);
                grid.content().add(new Canvas(figure));
            }
        }

        return grid;
    }
}
