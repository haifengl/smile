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
package smile.demo.projection;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;

@SuppressWarnings("serial")
public abstract class ProjectionDemo extends JPanel implements Runnable, ActionListener {

    private static String[] datasetName = {
        "IRIS", "US Arrests", "Food Nutrition",
        "Pen Digits", "COMBO-17"
    };

    private static String[] datasource = {
        "classification/iris.txt",
        "projection/USArrests.txt",
        "projection/food.txt",
        "classification/pendigits.txt",
        "projection/COMBO17.dat"
    };

    protected static AttributeDataset[] dataset = new AttributeDataset[datasetName.length];
    protected static int datasetIndex = 0;
    
    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    protected char pointLegend = '.';

    /**
     * Constructor.
     */
    public ProjectionDemo() {
        startButton = new JButton("Start");
        startButton.setActionCommand("startButton");
        startButton.addActionListener(this);

        datasetBox = new JComboBox<>();
        for (int i = 0; i < datasetName.length; i++) {
            datasetBox.addItem(datasetName[i]);
        }
        datasetBox.setSelectedIndex(0);
        datasetBox.setActionCommand("datasetBox");
        datasetBox.addActionListener(this);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(startButton);
        optionPane.add(new JLabel("Dataset:"));
        optionPane.add(datasetBox);

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);
    }

    /**
     * Execute the projection algorithm and return a swing JComponent representing
     * the clusters.
     */
    public abstract JComponent learn();

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);

        try {
        	JComponent plot = learn();
        	if (plot != null) {
        		if (canvas != null)
        			remove(canvas);
        		canvas = plot;
        		add(plot, BorderLayout.CENTER);
        	}
        	validate();
        } catch (Exception ex) {
        	System.err.println(ex);
        }
        
        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();
            
            if (dataset[datasetIndex] == null) {
                DelimitedTextParser parser = new DelimitedTextParser();
                parser.setDelimiter("[\t]+");
                if (datasetIndex < 5 && datasetIndex != 3) {
                    parser.setColumnNames(true);
                }
                if (datasetIndex == 1) {
                    parser.setRowNames(true);
                }
                if (datasetIndex == 0) {
                    parser.setResponseIndex(new NominalAttribute("class"), 4);
                }
                if (datasetIndex == 3) {
                    parser.setResponseIndex(new NominalAttribute("class"), 16);
                }
                if (datasetIndex >= 5) {
                    parser.setResponseIndex(new NominalAttribute("class"), 4);
                }

                try {
                    dataset[datasetIndex] = parser.parse(datasetName[datasetIndex], smile.data.parser.IOUtils.getTestDataFile(datasource[datasetIndex]));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                    System.out.println(ex);
                }
            }

            if (dataset[datasetIndex].size() < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }
            
            Thread thread = new Thread(this);
            thread.start();
        }
    }
}
