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
package smile.demo.manifold;

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
import javax.swing.JTextField;

import smile.data.AttributeDataset;
import smile.data.parser.DelimitedTextParser;

@SuppressWarnings("serial")
public abstract class ManifoldDemo extends JPanel implements Runnable, ActionListener {
    int k = 7;
    private JTextField knnField;

    private static String[] datasetName = {
        "Swiss Roll", "Face"
    };

    private static String[] datasource = {
        "manifold/swissroll.txt",
        "manifold/face.txt"
    };

    static AttributeDataset[] dataset = new AttributeDataset[datasetName.length];
    static int datasetIndex = 0;

    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = '.';

    /**
     * Constructor.
     */
    public ManifoldDemo() {
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

        knnField = new JTextField(Integer.toString(k), 5);
        optionPane.add(new JLabel("K:"));
        optionPane.add(knnField);

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
        knnField.setEnabled(false);

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
        knnField.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();

            if (dataset[datasetIndex] == null) {
                DelimitedTextParser parser = new DelimitedTextParser();
                parser.setDelimiter("[\t]+");

                try {
                    dataset[datasetIndex] = parser.parse(datasetName[datasetIndex], smile.data.parser.IOUtils.getTestDataFile(datasource[datasetIndex]));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                    System.err.println(ex);
                }
            }

            double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        
            if (data.length < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }

            try {
                k = Integer.parseInt(knnField.getText().trim());
                if (k < 2 || k > 30) {
                    JOptionPane.showMessageDialog(this, "Invalid K: " + k, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + knnField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }
}
