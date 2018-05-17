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

package smile.demo.classification;

import java.awt.Dimension;

import javax.swing.JFrame;

import smile.classification.NaiveBayes;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 * Use iris data set for demo and visualization purpose. 
 * 
 * @author rayeaster
 */
@SuppressWarnings("serial")
public class NaiveBayesDemo extends ClassificationDemo {
	
    /**
     * The number of classes.
     */
    private int k = 3;
    /**
     * The number of independent variables.
     */
    private int p = 2;
    /**
     * demo variables chosen from original iris data feature for easy-to-understand visualization: like Sepal.Length and Petal.Length
     */
    private static int[] pIdx = null;
	
    static {
    	pIdx = new int[] {0, 2};//this combination has the best training error among all 6 alternatives
    	
    	datasetName = new String[]{
    	        "Iris"
    	};

    	datasource = new String[]{
    	        "classification/iris.txt"
    	};
    	
    	parser = new DelimitedTextParser();
        parser.setColumnNames(true);// iris data set has column names at row 0
        parser.setResponseIndex(new NominalAttribute("class"), 4);// iris data set has response label at index position equals to 4 
    }
	    

    /**
     * Constructor.
     */
    public NaiveBayesDemo() {    	
    	super();
    }
    
    @Override
    protected PlotCanvas paintOnCanvas(double[][] data, int[] label) {
    	
    	int rows = data.length;
    	int features = data[0].length;
    	
    	double[][] paintPoints = new double[rows][features - 2];// iris data set has 4 features
    	for(int i = 0;i < rows;i++) {
    		for(int j = 0;j < pIdx.length;j++) {
    			paintPoints[i][j] = data[i][pIdx[j]];
    		}
    	}
    	
    	PlotCanvas canvas = ScatterPlot.plot(paintPoints, pointLegend);
        for (int i = 0; i < data.length; i++) {
            canvas.point(pointLegend, Palette.COLORS[label[i]], paintPoints[i]);
        }
        return canvas;
    }
    
    @Override
    protected double[] getContourLevels() {
        return new double[]{0.5, 2};	
    }

    @Override
    public double[][] learn(double[] x, double[] y) {

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        int[] labelPredict = new int[label.length];
        
        double[][] trainData = new double[label.length][p];
    	for(int i = 0;i < label.length;i++) {
    		for(int j = 0;j < pIdx.length;j++) {
    			trainData[i][j] = data[i][pIdx[j]];
    		}
    	}
    	
        NaiveBayes nbc = new NaiveBayes(NaiveBayes.Model.POLYAURN, k, p);
        nbc.learn(trainData, label);
        
        for (int i = 0; i < label.length; i++) {
        	labelPredict[i] = nbc.predict(trainData[i]);
        }
        double trainError = error(label, labelPredict);

        System.out.format("training error = %.2f%%\n", 100*trainError);
        
        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = nbc.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Naive Bayes";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new NaiveBayesDemo();
        JFrame f = new JFrame(demo.toString());
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
