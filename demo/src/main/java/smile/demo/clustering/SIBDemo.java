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

package smile.demo.clustering;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import smile.clustering.SIB;
import smile.data.SparseDataset;
import smile.data.parser.SparseDatasetParser;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SIBDemo extends JPanel implements Runnable, ActionListener {
    private JPanel optionPane;
    private JTextArea outputArea;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    private JTextField clusterNumberField;
    private int datasetIndex = 0;
    private int clusterNumber = 5;
    private String[] datasetName = {"NIPS", "KOS", "Enron"};
    private String[] datasource =  {
        "text/nips.txt",
        "text/kos.txt",
        "text/enron.txt"
    };

    private SparseDataset[] dataset = new SparseDataset[datasetName.length];

    public SIBDemo() {
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

        clusterNumberField = new JTextField(Integer.toString(clusterNumber), 5);
        optionPane.add(new JLabel("K:"));
        optionPane.add(clusterNumberField);

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane outputView = new JScrollPane(outputArea);
        add(outputView, BorderLayout.CENTER);
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);
        clustering();
        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            Thread thread = new Thread(this);
            thread.start();
        } else if ("datasetBox".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();
        }
    }

    private void clustering() {
        try {
            clusterNumber = Integer.parseInt(clusterNumberField.getText().trim());
            if (clusterNumber < 2) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + clusterNumber, "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid K: " + clusterNumberField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (dataset[datasetIndex] == null) {
            try {
                SparseDatasetParser parser = new SparseDatasetParser(1);
                dataset[datasetIndex] = parser.parse(datasetName[datasetIndex], smile.data.parser.IOUtils.getTestDataFile(datasource[datasetIndex]));
                for (int i = dataset[datasetIndex].size(); i-- > 0; ) {
                    if (dataset[datasetIndex].get(i).x.isEmpty()) {
                        dataset[datasetIndex].remove(i);
                    }
                }
                dataset[datasetIndex].unitize1();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.err.println(ex);
            }
        }

        System.out.println("The dataset " + datasetName[datasetIndex] + " has " + dataset[datasetIndex].size() + " documents and " + dataset[datasetIndex].ncols() + " words.");

        long clock = System.currentTimeMillis();
        SIB sib = new SIB(dataset[datasetIndex], clusterNumber, 20);
        outputArea.setText("");
        for (int j = 0; j < dataset[datasetIndex].ncols(); j++) {
            for (int i = 0; i < clusterNumber; i++) {
                outputArea.append(String.format("%.5f\t", sib.centroids()[i][j]));
            }
            outputArea.append("\n");
        }

        System.out.format("SIB clusterings %d samples in %dms\n", dataset[datasetIndex].size(), System.currentTimeMillis()-clock);
    }

    @Override
    public String toString() {
        return "Sequential Information Bottleneck";
    }

    public static void main(String argv[]) {
        SIBDemo demo = new SIBDemo();
        JFrame f = new JFrame("SIB");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
