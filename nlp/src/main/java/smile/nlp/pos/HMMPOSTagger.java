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

package smile.nlp.pos;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

/**
 * Part-of-speech tagging with hidden Markov model.
 *
 * @author Haifeng Li
 */
public class HMMPOSTagger implements POSTagger, Serializable {
    private static final Logger logger = LoggerFactory.getLogger(HMMPOSTagger.class);

    /**
	 * Serialization Version UID.
	 */
	private static final long serialVersionUID = 6600840654340610562L;
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
     * Returns natural log without underflow.
     */
    private static double log(double x) {
        double y = 0.0;
        if (x < 1E-300) {
            y = -690.7755;
        } else {
            y = java.lang.Math.log(x);
        }
        return y;
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
                delta[0][i] = log(pi[i]) + log(c[i][o[0][1]]);                
            } else {
                delta[0][i] = log(pi[i]) + log(b[i][o[0][0]]);
            }
        }

        for (int t = 1; t < n; t++) {
            for (int k = 0; k < numStates; k++) {
                double maxDelta = Double.NEGATIVE_INFINITY;
                int maxPsy = -1;

                for (int i = 0; i < numStates; i++) {
                    double thisDelta = delta[t - 1][i] + log(a[i][k]);

                    if (maxDelta < thisDelta) {
                        maxDelta = thisDelta;
                        maxPsy = i;
                    }
                }

                if (o[t][0] == 0 && o[t][1] >= 0) {
                    delta[t][k] = maxDelta + log(c[k][o[t][1]]);
                } else {
                    delta[t][k] = maxDelta + log(b[k][o[t][0]]);
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
            if (index != null) {
                seq[i][0] = index;
            } else {
                seq[i][0] = 0;
            }
            
            index = null;
            if (o[i].length() > 2) {
                index = suffix.get(o[i].substring(o[i].length() - 2));
            }            
            if (index != null) {
                seq[i][1] = index;
            } else {
                seq[i][1] = -1;
            }
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
     * Learns an HMM POS tagger by maximum likelihood estimation.
     */
    public static HMMPOSTagger learn(String[][] sentences, PennTreebankPOS[][] labels) {
        int index = 1;
        int suffixIndex = 0;
        Map<String, Integer> symbol = new HashMap<>();
        Map<String, Integer> suffix = new HashMap<>();
        for (int i = 0; i < sentences.length; i++) {
            for (int j = 0; j < sentences[i].length; j++) {
                Integer sym = symbol.get(sentences[i][j]);
                if (sym == null) {
                    symbol.put(sentences[i][j], index++);
                }
                
                if (sentences[i][j].length() > 2) {
                    String s = sentences[i][j].substring(sentences[i][j].length() - 2);
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

        Math.unitize1(pi);
        for (int i = 0; i < numStates; i++) {
            Math.unitize1(a[i]);
            Math.unitize1(b[i]);
            Math.unitize1(c[i]);
        }
        
        return new HMMPOSTagger(symbol, suffix, pi, a, b, c);
    }
    
    /**
     * Load training data from a corpora.
     * @param dir a file object defining the top directory
     */
    public static void load(String dir, List<String[]> sentences, List<PennTreebankPOS[]> labels) {
        List<File> files = new ArrayList<>();
        walkin(new File(dir), files);

        for (File file : files) {
            try {
                FileInputStream stream = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line = null;
                List<String> sent = new ArrayList<>();
                List<PennTreebankPOS> label = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        if (!sent.isEmpty()) {
                            sentences.add(sent.toArray(new String[sent.size()]));
                            labels.add(label.toArray(new PennTreebankPOS[label.size()]));
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
                    sentences.add(sent.toArray(new String[sent.size()]));
                    labels.add(label.toArray(new PennTreebankPOS[label.size()]));
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
     * @param dir a file object defining the top directory
     **/
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
     */
    public static void main(String[] argvs) {
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> labels = new ArrayList<>();
        
        load("D:\\sourceforge\\corpora\\PennTreebank\\PennTreebank2\\TAGGED\\POS\\WSJ", sentences, labels);        
        load("D:\\sourceforge\\corpora\\PennTreebank\\PennTreebank2\\TAGGED\\POS\\BROWN", sentences, labels);
        
        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = labels.toArray(new PennTreebankPOS[labels.size()][]);
        
        HMMPOSTagger tagger = HMMPOSTagger.learn(x, y);

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
