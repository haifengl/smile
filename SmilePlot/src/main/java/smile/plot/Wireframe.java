/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.plot;

import java.awt.Color;
import smile.math.Math;

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
     * @param vertices an m x n x 2 or m x n x 3 array which are coordinates of m x n grid.
     */
    public static PlotCanvas plot(double[][] vertices, int[][] edges) {
        double[] lowerBound = Math.colMin(vertices);
        double[] upperBound = Math.colMax(vertices);

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Wireframe frame = new Wireframe(vertices, edges);
        canvas.add(frame);

        return canvas;
    }
    
    /**
     * Create a 2D grid plot canvas.
     * @param id the id of the plot.
     * @param vertices an m x n x 2 array which are coordinates of m x n grid.
     */
    public static PlotCanvas plot(String id, double[][] vertices, int[][] edges) {
        double[] lowerBound = Math.colMin(vertices);
        double[] upperBound = Math.colMax(vertices);

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Wireframe frame = new Wireframe(vertices, edges);
        frame.setID(id);
        canvas.add(frame);

        return canvas;
    }
}