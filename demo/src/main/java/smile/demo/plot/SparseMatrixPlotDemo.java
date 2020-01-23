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

package smile.demo.plot;

import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import smile.math.matrix.SparseMatrix;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.SparseMatrixPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SparseMatrixPlotDemo extends JPanel {
    public SparseMatrixPlotDemo() {
        super(new GridLayout(1,2));

        try {
            SparseMatrix m1 = SparseMatrix.text(smile.util.Paths.getTestData("matrix/08blocks.txt"));
            PlotCanvas canvas = SparseMatrixPlot.plot(m1);
            canvas.setTitle("08blocks");
            add(canvas);

            SparseMatrix m2 = SparseMatrix.text(smile.util.Paths.getTestData("matrix/mesh2em5.txt"));
            canvas = SparseMatrixPlot.plot(m2, Palette.jet(256));
            canvas.setTitle("mesh2em5");
            add(canvas);
        } catch (Exception ex) {
            System.err.println(ex);
            ex.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "Sparse Matrix Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Surface Plot");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new SparseMatrixPlotDemo());
        frame.setVisible(true);
    }
}
