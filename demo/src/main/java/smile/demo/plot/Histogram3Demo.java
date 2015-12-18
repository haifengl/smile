/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.demo.plot;

import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.Math;
import smile.plot.Histogram3D;
import smile.plot.Palette;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class Histogram3Demo extends JPanel {
    public Histogram3Demo() {
        super(new GridLayout(1,2));
        
        double[][] data = new double[10000][2];
        for (int j = 0; j < data.length; j++) {
            double x, y, r;
            do {
                x = 2 * (Math.random() - 0.5);
                y = 2 * (Math.random() - 0.5);
                r = x * x + y * y;
            } while (r >= 1.0);

            double z = Math.sqrt(-2.0 * Math.log(r) / r);
            data[j][0] = new Double(x * z);
            data[j][1] = new Double(y * z);
        }
        
        PlotCanvas canvas = Histogram3D.plot(data, 20);
        canvas.setTitle("Histogram 3D");
        add(canvas);
        
        canvas = Histogram3D.plot(data, 20, Palette.jet(16));
        canvas.setTitle("Histogram 3D with Colormap");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Histogram 3D";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Histogram 3D");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new Histogram3Demo());
        frame.setVisible(true);
    }
}
