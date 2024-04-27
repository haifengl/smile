/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.nlp.pos;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import smile.math.MathEx;
import smile.util.Paths;

/**
 * Part-of-speech tagging with hidden Markov model.
 *
 * @author Haifeng Li
 */
public class HMMPOSTagger implements POSTagger, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HMMPOSTagger.class);

    /**
     * The emission symbols of HMM and corresponding indices.
     */
    private Map<String, Integer> symbol;
    /**
     * The emission symbol suffix of HMM and corresponding indices.
     */
    private Map<String, Integer> suffix;
    /**
     * Initial state probabilities.
     */
    private double[] pi;
    /**
     * First order state transition probabilities.
     */
    private double[][] a;
    /**
     * Symbol emission probabilities.
     */
    private double[][] b;
    /**
     * Suffix emission probabilities.
     */
    private double[][] c;

    /**
     * Default English POS tagger.
     */
    private static HMMPOSTagger DEFAULT_TAGGER;
    
    /**
     * Constructor. Creates an empty model. For Serialization only.
     */
    public HMMPOSTagger() {
        
    }
    
    /**
     * Constructor.
     * @param symbol the emission symbols of HMM and corresponding indices
     * (starting at 1). The index 0 is reserved for unknown words.
     * @param suffix the emission suffix of HMM and corresponding indices
     * (starting at 0).
     * @param pi the initial state probabilities.
     * @param a the state transition probabilities.
     * @param b the symbol emission probabilities.
     * @param c the symbol suffix emission probabilities.
     */
    private HMMPOSTagger(Map<String, Integer> symbol, Map<String, Integer> suffix, double[] pi, double[][] a, double[][] b, double[][] c) {
        if (pi.length != PennTreebankPOS.values().length) {
            throw new IllegalArgumentException("The number of states is different from the size of Penn Treebank tagset.");
        }

        if (a[0].length != PennTreebankPOS.values().length) {
            throw new IllegalArgumentException("Invlid state transition probability size.");
        }

        if (b[0].length != symbol.size() + 1) {
            throw new IllegalArgumentException("Invlid symbol emission probability size.");
        }

        if (c[0].length != suffix.size()) {
            throw new IllegalArgumentException("Invlid symbol suffix emission probability size.");
        }

        this.pi = pi;
        this.a = a;
        this.b = b;
        this.c = c;
        this.symbol = symbol;
        this.suffix = suffix;
    }

    /**
     * Returns the default English POS tagger.
     * @return the default English POS tagger 
     */
    public static HMMPOSTagger getDefault() {
        if (DEFAULT_TAGGER == null) {
            try {
                ObjectInputStream ois = new ObjectInputStream(HMMPOSTagger.class.getResourceAsStream("/smile/nlp/pos/hmmpostagger.model"));
                DEFAULT_TAGGER = (HMMPOSTagger) ois.readObject();
                ois.close();
            } catch (Exception ex) {
                logger.error("Failed to load /smile/nlp/pos/hmmpostagger.model", ex);
            }
        }
        return DEFAULT_TAGGER;
    }
    
    @Override
    public PennTreebankPOS[] tag(String[] sentence) {
        int[] s = viterbi(sentence);   
        
        int n = sentence.length;
        PennTreebankPOS[] pos = new PennTreebankPOS[n];
        for (int i = 0; i < n; i++) {
            if (symbol.get(sentence[i]) == null) {
                pos[i] = RegexPOSTagger.tag(sentence[i]);
            }
            
            if (pos[i] == null) {
                pos[i] = PennTreebankPOS.values()[s[i]];
            }
        }
        
        return pos;
    }

    /**
     * Returns the most likely state sequence given the observation sequence by
     * the Viterbi algorithm, which maximizes the probability of
     * <code>P(I | O, HMM)</code>. Note that only s[i] taking value -1 will be
     * inferred. In the calculation, we may get ties. In this case, one of them
     * is chosen randomly.
     *
     * @param sentence an observation sequence.
     * @return the most likely state sequence.
     */
    private int[] viterbi(String[] sentence) {
        int n = sentence.length;
        int[][] o = translate(symbol, suffix, sentence);
        int[] s = new int[n];
        
        int numStates = pi.length;
        // The porbability of the most probable path.
        double[][] delta = new double[n][numStates];
        // Backtrace.
        int[][] psy = new int[n][numStates];

        for (int i = 0; i < numStates; i++) {
            if (o[0][0] == 0 && o[0][1] >= 0) {
                delta[0][i] = MathEx.log(pi[i]) + MathEx.log(c[i][o[0][1]]);
            } else {
                delta[0][i] = MathEx.log(pi[i]) + MathEx.log(b[i][o[0][0]]);
            }
        }

        for (int t = 1; t < n; t++) {
            for (int k = 0; k < numStates; k++) {
                double maxDelta = Double.NEGATIVE_INFINITY;
                int maxPsy = -1;

                for (int i = 0; i < numStates; i++) {
                    double thisDelta = delta[t - 1][i] + MathEx.log(a[i][k]);

                    if (maxDelta < thisDelta) {
                        maxDelta = thisDelta;
                        maxPsy = i;
                    }
                }

                if (o[t][0] == 0 && o[t][1] >= 0) {
                    delta[t][k] = maxDelta + MathEx.log(c[k][o[t][1]]);
                } else {
                    delta[t][k] = maxDelta + MathEx.log(b[k][o[t][0]]);
                }

                psy[t][k] = maxPsy;
            }
        }
        
        n = o.length - 1;
        double maxDelta = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < numStates; i++) {
            if (maxDelta < delta[n][i]) {
                maxDelta = delta[n][i];
                s[n] = i;
            }
        }

        for (int t = n; t-- > 0;) {
            s[t] = psy[t + 1][s[t + 1]];
        }
        
        return s;
    }

    /**
     * Translate an observation sequence to internal representation.
     */
    private static int[][] translate(Map<String, Integer> symbol, Map<String, Integer> suffix, String[] o) {
        int[][] seq = new int[o.length][2];
        
        for (int i = 0; i < o.length; i++) {
            Integer index = symbol.get(o[i]);
            seq[i][0] = Objects.requireNonNullElse(index, 0);
            
            index = null;
            if (o[i].length() > 2) {
                index = suffix.get(o[i].substring(o[i].length() - 2));
            }
            seq[i][1] = Objects.requireNonNullElse(index, -1);
        }

        return seq;
    }
    
    /**
     * Translate a POS tag sequence to internal representation.
     */
    private static int[] translate(PennTreebankPOS[] tags) {
        int[] seq = new int[tags.length];
        
        for (int i = 0; i < tags.length; i++) {
            seq[i] = tags[i].ordinal();
        }

        return seq;
    }

    /**
     * Fits an HMM POS tagger by maximum likelihood estimation.
     * @param sentences the training sentences.
     * @param labels the training labels.
     * @return the model.
     */
    public static HMMPOSTagger fit(String[][] sentences, PennTreebankPOS[][] labels) {
        int index = 1;
        int suffixIndex = 0;
        Map<String, Integer> symbol = new HashMap<>();
        Map<String, Integer> suffix = new HashMap<>();
        for (String[] sentence : sentences) {
            for (String word : sentence) {
                Integer sym = symbol.get(word);
                if (sym == null) {
                    symbol.put(word, index++);
                }

                if (word.length() > 2) {
                    String s = word.substring(word.length() - 2);
                    sym = suffix.get(s);
                    if (sym == null) {
                        suffix.put(s, suffixIndex++);
                    }
                }
            }
        }

        int numStates = PennTreebankPOS.values().length;

        double[] pi = new double[numStates];
        double[][] a = new double[numStates][numStates];
        double[][] b = new double[numStates][symbol.size() + 1];
        double[][] c = new double[numStates][suffix.size()];
        
        PennTreebankPOS[] tags = PennTreebankPOS.values();
        for (int i = 0; i < numStates; i++) {
            if (tags[i].open) {
                b[i][0] = 1.0;
            }
        }

        for (int i = 0; i < sentences.length; i++) {
            int[] tag = translate(labels[i]);
            int[][] obs = translate(symbol, suffix, sentences[i]);

            pi[tag[0]]++;
            b[tag[0]][obs[0][0]]++;            
            if (obs[0][1] >= 0) {
                c[tag[0]][obs[0][1]]++;
            }
            
            for (int j = 1; j < obs.length; j++) {
                a[tag[j-1]][tag[j]]++;
                b[tag[j]][obs[j][0]]++;
                if (obs[j][1] >= 0) {
                    c[tag[j]][obs[j][1]]++;
                }
            }                
        }

        MathEx.unitize1(pi);
        for (int i = 0; i < numStates; i++) {
            MathEx.unitize1(a[i]);
            MathEx.unitize1(b[i]);
            MathEx.unitize1(c[i]);
        }
        
        return new HMMPOSTagger(symbol, suffix, pi, a, b, c);
    }
    
    /**
     * Load training data from a corpora.
     * @param dir the top directory of training data.
     * @param sentences the output list of training sentences.
     * @param labels the output list of training labels.
     */
    public static void read(Path dir, List<String[]> sentences, List<PennTreebankPOS[]> labels) {
        List<File> files = new ArrayList<>();
        walkin(dir.toFile(), files);

        for (File file : files) {
            try {
                FileInputStream stream = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line;
                List<String> sent = new ArrayList<>();
                List<PennTreebankPOS> label = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        if (!sent.isEmpty()) {
                            sentences.add(sent.toArray(new String[0]));
                            labels.add(label.toArray(new PennTreebankPOS[0]));
                            sent.clear();
                            label.clear();
                        }
                    } else if (!line.startsWith("===") && !line.startsWith("*x*")) {
                        String[] words = line.split("\\s");
                        for (String word : words) {
                            String[] w = word.split("/");
                            if (w.length == 2) {
                                sent.add(w[0]);
                                
                                int pos = w[1].indexOf('|');
                                String tag = pos == -1 ? w[1] : w[1].substring(0, pos);
                                if (tag.equals("PRP$R")) tag = "PRP$";
                                if (tag.equals("JJSS")) tag = "JJS";
                                label.add(PennTreebankPOS.getValue(tag));
                            }
                        }
                    }
                }
                
                if (!sent.isEmpty()) {
                    sentences.add(sent.toArray(new String[0]));
                    labels.add(label.toArray(new PennTreebankPOS[0]));
                    sent.clear();
                    label.clear();
                }
                
                reader.close();
            } catch (Exception e) {
                logger.error("Failed to load training data {}", file, e);
            }
        }
    }

    /**  
     * Recursive function to descend into the directory tree and find all the files
     * that end with ".POS"
     * @param dir the top directory of training data.
     * @param files the output list of training files.
     */
    public static void walkin(File dir, List<File> files) {
        String pattern = ".POS";
        File[] listFile = dir.listFiles();
        if (listFile != null) {
            for (File file : listFile) {                
                if (file.isDirectory()) {
                    walkin(file, files);
                } else {
                    if (file.getName().endsWith(pattern)) {
                        files.add(file);
                    }
                }
            }
        }
    }
    
    /**
     * Train the default model on WSJ and BROWN datasets.
     * @param args the command line arguments.
     */
    public static void main(String[] args) {
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> labels = new ArrayList<>();
        
        read(Paths.getTestData("nlp/PennTreebank/PennTreebank2/TAGGED/POS/WSJ"), sentences, labels);
        read(Paths.getTestData("nlp/PennTreebank/PennTreebank2/TAGGED/POS/BROWN"), sentences, labels);
        
        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = labels.toArray(new PennTreebankPOS[labels.size()][]);
        
        HMMPOSTagger tagger = HMMPOSTagger.fit(x, y);

        try {
            FileOutputStream fos = new FileOutputStream("hmmpostagger.model");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(tagger);
            oos.flush();
            oos.close();
        } catch (Exception ex) {
            logger.error("Failed to save HMM POS model", ex);
        }
    }
}
