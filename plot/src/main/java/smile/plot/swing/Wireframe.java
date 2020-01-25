/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.plot.swing;

import java.awt.Color;
import smile.math.MathEx;

/**
 * A wire frame model specifies each edge of the physical object where two
 * mathematically continuous smooth surfaces meet, or by connecting an
 * object's constituent vertices using straight lines or curves.
 *
 * @author Haifeng Li
 */
public class Wireframe extends Plot {

    /**
     * The coordinates of vertices in the wire frame.
     */
    private double[][] vertices;
    /**
     * The vertex indices of two end points of each edge in the wire frame.
     */
    private int[][] edges;

    /**
     * Constructor.
     * @param vertices a n-by-2 or n-by-3 array which are coordinates of n vertices.
     * @param edges an m-by-2 array of which each row is the vertex indices of two
     * end points of each edge.
     */
    public Wireframe(double[][] vertices, int[][] edges) {
        this(vertices, edges, Color.BLACK);
    }

    /**
     * Constructor.
     * @param vertices a n-by-2 or n-by-3 array which are coordinates of n vertices.
     * @param edges an m-by-2 array of which each row is the vertex indices of two
     * end points of each edge.
     */
    public Wireframe(double[][] vertices, int[][] edges, Color color) {
        super(color);
        this.vertices = vertices;
        this.edges = edges;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        for (int i = 0; i < edges.length; i++) {
            g.drawLine(vertices[edges[i][0]], vertices[edges[i][1]]);
        }

        g.setColor(c);
    }
    
    /**
     * Create a wire frame plot canvas.
     * @param vertices a n-by-2 or n-by-3 array which are coordinates of n vertices.
     */
    public static PlotCanvas plot(double[][] vertices, int[][] edges) {
        double[] lowerBound = MathEx.colMin(vertices);
        double[] upperBound = MathEx.colMax(vertices);

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Wireframe frame = new Wireframe(vertices, edges);
        canvas.add(frame);

        return canvas;
    }
    
    /**
     * Create a 2D grid plot canvas.
     * @param id the id of the plot.
     * @param vertices a n-by-2 or n-by-3 array which are coordinates of n vertices.
     * @param edges an m-by-2 array of which each row is the vertex indices of two
     * end points of each edge.
     */
    public static PlotCanvas plot(String id, double[][] vertices, int[][] edges) {
        double[] lowerBound = MathEx.colMin(vertices);
        double[] upperBound = MathEx.colMax(vertices);

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Wireframe frame = new Wireframe(vertices, edges);
        frame.setID(id);
        canvas.add(frame);

        return canvas;
    }
}
