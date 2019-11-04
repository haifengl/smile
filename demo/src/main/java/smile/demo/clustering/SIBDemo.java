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

package smile.demo.clustering;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.stream.Collectors;

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
import smile.data.Instance;
import smile.util.SparseArray;

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
                int arrayIndexOrigin = 1;
                dataset[datasetIndex] = SparseDataset.from(smile.util.Paths.getTestData(datasource[datasetIndex]), arrayIndexOrigin);
                dataset[datasetIndex] = SparseDataset.of(
                        dataset[datasetIndex].stream().filter(a -> !a.isEmpty()).collect(Collectors.toList()),
                        dataset[datasetIndex].ncols()
                );
                dataset[datasetIndex].unitize1();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.err.println(ex);
            }
        }

        System.out.println("The dataset " + datasetName[datasetIndex] + " has " + dataset[datasetIndex].size() + " documents and " + dataset[datasetIndex].ncols() + " words.");

        long clock = System.currentTimeMillis();
        SparseArray[] data = dataset[datasetIndex].stream().toArray(SparseArray[]::new);
        SIB sib = SIB.fit(data, clusterNumber, 20);
        outputArea.setText("");
        for (int j = 0; j < dataset[datasetIndex].ncols(); j++) {
            for (int i = 0; i < clusterNumber; i++) {
                outputArea.append(String.format("%.5f\t", sib.centroids[i][j]));
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
