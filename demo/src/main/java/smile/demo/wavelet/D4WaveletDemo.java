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

package smile.demo.wavelet;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.LinePlot;
import smile.plot.PlotCanvas;
import smile.wavelet.D4Wavelet;
import smile.wavelet.Wavelet;
import smile.wavelet.WaveletShrinkage;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class D4WaveletDemo extends JPanel {
    public D4WaveletDemo() {
        super(new GridLayout(1,2));

        double[] x = new double[1024];
        x[3] = 1.0;

        Wavelet wavelet = new D4Wavelet();
        wavelet.inverse(x);

        PlotCanvas canvas = LinePlot.plot(x);
        canvas.setTitle("D4");
        add(canvas);

        double[] sp500 = {
            1103.96, 1107.84, 1114.11, 1108.61, 1106.36, 1097.86, 1105.31,
            1114.51, 1118.84, 1121.08, 1127.53, 1128.55, 1125.53, 1126.60,
            1116.56, 1132.66, 1135.71, 1136.27, 1140.52, 1145.96, 1143.81,
            1137.31, 1145.68, 1147.72, 1136.03, 1147.95, 1138.68, 1115.49,
            1092.40, 1095.80, 1091.94, 1096.93, 1087.61, 1073.89, 1090.05,
            1100.67, 1097.25, 1064.12, 1065.51, 1060.06, 1069.68, 1067.10,
            1075.95, 1079.13, 1096.14, 1099.03, 1105.49, 1110.00, 1107.49,
            1095.89, 1101.24, 1103.10, 1105.36, 1117.01, 1119.36, 1119.12,
            1125.12, 1138.40, 1137.56, 1140.22, 1143.96, 1151.71, 1148.53,
            1150.83, 1159.94, 1166.13, 1166.68, 1157.25, 1166.47, 1172.70,
            1170.03, 1167.58, 1167.71, 1173.75, 1171.75, 1171.23, 1178.71,
            1186.01, 1188.23, 1181.75, 1187.47, 1194.94, 1195.94, 1198.69,
            1210.77, 1210.17, 1192.06, 1199.04, 1207.16, 1202.52, 1207.87,
            1217.07, 1209.92, 1184.59, 1193.30, 1206.77, 1188.58, 1197.50,
            1169.24, 1164.38, 1127.04, 1122.27, 1156.39, 1155.43, 1170.04,
            1157.19, 1136.52, 1138.78, 1119.57, 1107.34, 1067.26, 1084.78,
            1067.42, 1075.51, 1074.27, 1102.59, 1087.30, 1073.01, 1098.82,
            1098.43, 1065.84, 1050.81, 1062.75, 1058.77, 1082.65, 1095.00,
            1091.21, 1114.02, 1115.98, 1116.16, 1122.79, 1113.90, 1095.57,
            1090.93, 1075.10, 1077.50, 1071.10, 1040.56, 1031.10, 1027.65,
            1028.09, 1028.54, 1062.92, 1070.50, 1077.23, 1080.65, 1095.61,
            1094.46, 1093.85, 1066.85, 1064.53, 1086.67, 1072.14, 1092.17,
            1102.89, 1117.36, 1112.84, 1108.07, 1098.44, 1107.53, 1125.34,
            1121.06, 1125.78, 1122.07, 1122.80, 1122.92, 1116.89, 1081.48,
            1082.22, 1077.49, 1081.16, 1092.08, 1092.44, 1075.63, 1073.36,
            1063.20, 1048.98, 1056.28, 1049.27, 1062.90, 1046.88, 1049.72,
            1080.66, 1093.61, 1102.60, 1092.36, 1101.15, 1104.57, 1113.38,
            1121.16, 1119.43, 1123.89, 1126.39, 1126.57, 1142.82, 1139.49,
            1131.10, 1131.69, 1148.64, 1142.31, 1146.75, 1145.97, 1143.49,
            1144.96, 1140.68, 1159.81, 1161.57, 1158.36, 1165.32, 1164.28,
            1171.32, 1177.82, 1177.47, 1176.83, 1178.64, 1166.74, 1179.82,
            1180.52, 1184.74, 1184.88, 1183.84, 1184.47, 1183.87, 1185.71,
            1187.86, 1193.79, 1198.34, 1221.20, 1223.24, 1223.59, 1213.14,
            1213.04, 1209.07, 1200.44, 1194.79, 1178.33, 1183.75, 1196.12,
            1198.07, 1192.51, 1183.70, 1194.16, 1189.08, 1182.96, 1186.60,
            1206.81, 1219.93, 1223.87, 1227.25, 1225.02, 1230.14, 1233.85,
            1242.52, 1241.84, 1241.58, 1236.34
        };
        canvas = LinePlot.plot(sp500);
        double[] smooth = sp500.clone();
        WaveletShrinkage.denoise(smooth, wavelet);
        canvas.line(smooth, Color.BLUE);
        canvas.setTitle("S&P 500");
        add(canvas);
    }

    @Override
    public String toString() {
        return "D4";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("D4 Wavelet");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.add(new D4WaveletDemo());
        frame.setVisible(true);
    }
}