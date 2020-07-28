/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/
package smile.demo.timeseries;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.swing.BarPlot;
import smile.plot.swing.Canvas;
import smile.plot.swing.Line;
import smile.plot.swing.LinePlot;
import smile.timeseries.ARIMA;

/**
 * This demo showcase ARIMA difference, ACF and PACF chart
 * 
 * @author rayeaster
 *
 */
@SuppressWarnings("serial")
public class ARIMADemo extends JPanel {

    private static final String name = "ARIMA Demo";

    private static double[] female_birth_val = new double[]{35.0, 32.0, 30.0, 31.0, 44.0, 29.0, 45.0, 43.0, 38.0, 27.0,
            38.0, 33.0, 55.0, 47.0, 45.0, 37.0, 50.0, 43.0, 41.0, 52.0, 34.0, 53.0, 39.0, 32.0, 37.0, 43.0, 39.0, 35.0,
            44.0, 38.0, 24.0, 23.0, 31.0, 44.0, 38.0, 50.0, 38.0, 51.0, 31.0, 31.0, 51.0, 36.0, 45.0, 51.0, 34.0, 52.0,
            47.0, 45.0, 46.0, 39.0, 48.0, 37.0, 35.0, 52.0, 42.0, 45.0, 39.0, 37.0, 30.0, 35.0, 28.0, 45.0, 34.0, 36.0,
            50.0, 44.0, 39.0, 32.0, 39.0, 45.0, 43.0, 39.0, 31.0, 27.0, 30.0, 42.0, 46.0, 41.0, 36.0, 45.0, 46.0, 43.0,
            38.0, 34.0, 35.0, 56.0, 36.0, 32.0, 50.0, 41.0, 39.0, 41.0, 47.0, 34.0, 36.0, 33.0, 35.0, 38.0, 38.0, 34.0,
            53.0, 34.0, 34.0, 38.0, 35.0, 32.0, 42.0, 34.0, 46.0, 30.0, 46.0, 45.0, 54.0, 34.0, 37.0, 35.0, 40.0, 42.0,
            58.0, 51.0, 32.0, 35.0, 38.0, 33.0, 39.0, 47.0, 38.0, 52.0, 30.0, 34.0, 40.0, 35.0, 42.0, 41.0, 42.0, 38.0,
            24.0, 34.0, 43.0, 36.0, 55.0, 41.0, 45.0, 41.0, 37.0, 43.0, 39.0, 33.0, 43.0, 40.0, 38.0, 45.0, 46.0, 34.0,
            35.0, 48.0, 51.0, 36.0, 33.0, 46.0, 42.0, 48.0, 34.0, 41.0, 35.0, 40.0, 34.0, 30.0, 36.0, 40.0, 39.0, 45.0,
            38.0, 47.0, 33.0, 30.0, 42.0, 43.0, 41.0, 41.0, 59.0, 43.0, 45.0, 38.0, 37.0, 45.0, 42.0, 57.0, 46.0, 51.0,
            41.0, 47.0, 26.0, 35.0, 44.0, 41.0, 42.0, 36.0, 45.0, 45.0, 45.0, 47.0, 38.0, 42.0, 35.0, 36.0, 39.0, 45.0,
            43.0, 47.0, 36.0, 41.0, 50.0, 39.0, 41.0, 46.0, 64.0, 45.0, 34.0, 38.0, 44.0, 48.0, 46.0, 44.0, 37.0, 39.0,
            44.0, 45.0, 33.0, 44.0, 38.0, 46.0, 46.0, 40.0, 39.0, 44.0, 48.0, 50.0, 41.0, 42.0, 51.0, 41.0, 44.0, 38.0,
            68.0, 40.0, 42.0, 51.0, 44.0, 45.0, 36.0, 57.0, 44.0, 42.0, 53.0, 42.0, 34.0, 40.0, 56.0, 44.0, 53.0, 55.0,
            39.0, 59.0, 55.0, 73.0, 55.0, 44.0, 43.0, 40.0, 47.0, 51.0, 56.0, 49.0, 54.0, 56.0, 47.0, 44.0, 43.0, 42.0,
            45.0, 50.0, 48.0, 43.0, 40.0, 59.0, 41.0, 42.0, 51.0, 49.0, 45.0, 43.0, 42.0, 38.0, 47.0, 38.0, 36.0, 42.0,
            35.0, 28.0, 44.0, 36.0, 45.0, 46.0, 48.0, 49.0, 43.0, 42.0, 59.0, 45.0, 52.0, 46.0, 42.0, 40.0, 40.0, 45.0,
            35.0, 35.0, 40.0, 39.0, 33.0, 42.0, 47.0, 51.0, 44.0, 40.0, 57.0, 49.0, 45.0, 49.0, 51.0, 46.0, 44.0, 52.0,
            45.0, 32.0, 46.0, 41.0, 34.0, 33.0, 36.0, 49.0, 43.0, 43.0, 34.0, 39.0, 35.0, 52.0, 47.0, 52.0, 39.0, 40.0,
            42.0, 42.0, 53.0, 39.0, 40.0, 38.0, 44.0, 34.0, 37.0, 52.0, 48.0, 55.0, 50.0};

    private static double[] sunspot_val = new double[]{5.0, 58.0, 3.0, 27.0, 28.0, 40.0, 47.0, 34.0, 73.0, 11.0, 83.4,
            9.6, 62.9, 20.9, 100.8, 7.0, 84.8, 24.1, 89.9, 21.3, 14.5, 42.2, 0.0, 35.4, 15.6, 16.6, 70.9, 56.9, 64.6,
            40.1, 66.6, 6.7, 95.8, 30.5, 139.0, 17.0, 32.3, 52.2, 7.1, 64.0, 9.5, 63.5, 18.6, 47.4, 37.6, 44.3, 35.7,
            36.1, 67.8, 33.2, 83.9, 38.0, 112.3, 15.1, 104.5, 15.5, 154.6, 17.9, 142.2, 17.5, 119.6, 29.8, 16.5, 11.0,
            29.0, 0.0, 47.0, 26.0, 78.0, 35.0, 70.0, 40.0, 22.0, 47.7, 10.2, 85.9, 11.4, 81.6, 19.8, 68.1, 82.9, 66.6,
            16.0, 34.0, 28.1, 1.4, 45.8, 6.6, 36.3, 47.8, 121.5, 36.7, 61.5, 64.5, 4.3, 77.2, 16.3, 111.2, 11.3, 54.3,
            25.4, 35.6, 41.8, 2.7, 53.8, 5.7, 57.1, 26.1, 63.9, 21.2, 79.7, 47.5, 92.6, 69.4, 141.7, 53.9, 47.0, 66.6,
            12.6, 140.4, 13.4, 145.8, 8.6, 110.9, 15.2, 55.7, 16.0, 20.0, 0.0, 63.0, 22.0, 122.0, 11.0, 81.0, 20.0,
            40.0, 47.8, 32.4, 61.2, 37.8, 66.5, 92.5, 38.5, 132.0, 60.0, 6.4, 45.0, 10.1, 5.0, 41.1, 4.0, 49.6, 27.5,
            138.3, 24.2, 98.5, 54.1, 22.7, 59.1, 7.3, 101.6, 12.4, 59.7, 13.1, 73.0, 26.2, 5.0, 62.0, 3.6, 103.9, 14.2,
            69.0, 11.1, 114.4, 30.6, 151.6, 31.5, 190.2, 37.6, 93.8, 68.9, 27.5, 115.9, 29.4, 94.5, 21.6, 104.1, 7.6,
            57.6, 23.0, 10.0, 2.0, 60.0, 11.0, 103.0, 5.0, 111.0, 16.0, 60.0, 30.7, 47.6, 45.1, 69.8, 34.8, 154.4, 22.8,
            130.9, 46.9, 4.1, 43.1, 8.1, 12.2, 30.1, 1.8, 64.2, 8.5, 103.2, 10.7, 124.7, 39.0, 54.8, 44.0, 37.6, 66.2,
            3.4, 63.7, 6.8, 85.1, 26.7, 24.4, 48.5, 1.4, 80.6, 5.8, 77.8, 5.7, 109.6, 16.3, 136.3, 13.9, 184.8, 27.9,
            105.9, 38.0, 92.5, 66.6, 100.2, 54.7, 64.2, 63.6, 2.9, 64.7, 36.0, 8.0, 11.0, 39.0, 21.0, 73.0, 16.0, 101.0,
            5.0, 80.9, 12.2, 54.0, 36.4, 106.1, 30.6, 125.9, 10.2, 118.1, 41.0, 6.8, 47.5, 2.5, 13.9, 23.9, 8.5, 67.0,
            13.2, 85.7, 15.0, 96.3, 20.6, 93.8, 47.0, 74.0, 44.7, 6.0, 63.5, 6.3, 78.0, 12.1, 42.0, 43.9, 9.6, 63.6,
            16.7, 64.9, 8.7, 88.8, 9.6, 134.7, 4.4, 159.0, 10.2, 105.5, 34.5, 155.4, 45.9, 157.6, 29.9, 93.4, 40.4, 3.1,
            79.3};

    public static void main(String[] args) {
        JFrame frame = new JFrame(name);
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ARIMADemo());
        frame.setVisible(true);
    }

    public ARIMADemo() {
        super(new GridLayout(2, 2));
        ARIMA model = newARIMA(1, 1, 1, female_birth_val);
        plotARIMAStats(model);
    }

    private void plotARIMAStats(ARIMA model) {
        double[] originalData = model.getData();
        double[] differenced = model.difference();
        int lag = 25;
        double[] acfs = new double[lag + 1];
        double[] pacfs = new double[lag + 1];
        for (int i = 0; i <= lag; i++) {
            acfs[i] = ARIMA.acf(originalData, i);
            pacfs[i] = ARIMA.pacf(originalData, i);
        }
        addLineChart(originalData, "Original", Line.Style.DASH, Color.RED);
        addLineChart(differenced, "Differenced", Line.Style.DASH, Color.BLUE);
        addBarChart(acfs, "ACF");
        addBarChart(pacfs, "PACF");
    }

    private void addLineChart(double[] d, String title, Line.Style lineStyle, Color color) {
        double[][] data = new double[d.length][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = j;
            data[j][1] = d[j];
        }
        Canvas canvas = LinePlot.of(data, lineStyle, color, title).canvas();
        canvas.setTitle(title);
        add(canvas.panel());
    }

    private void addBarChart(double[] data, String title) {
        String[] labels = new String[data.length];
        for (int j = 0; j < data.length; j++) {
            labels[j] = String.valueOf(j);
        }
        Canvas canvas = BarPlot.of(data).canvas();
        canvas.setTitle(title);
        int k = labels.length;
        double[] locations = new double[k];
        for (int i = 0; i < k; i++) {
            locations[i] = i + 0.5;
        }
        canvas.getAxis(0).setTicks(labels, locations);
        add(canvas.panel());
    }

    private static ARIMA newARIMA(int p, int d, int q, double[] timeseries) {

        ARIMA.Builder newModel = new ARIMA.Builder(timeseries);
        if (p > 0) {
            newModel = newModel.p(p);
        }
        if (d > 0) {
            newModel = newModel.d(d);
        }
        if (q > 0) {
            newModel = newModel.q(q);
        }
        ARIMA ret = newModel.build();
        return ret;
    }

    @Override
    public String toString() {
        return name;
    }

}
