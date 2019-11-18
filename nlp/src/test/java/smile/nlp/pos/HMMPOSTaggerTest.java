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

package smile.nlp.pos;

import smile.validation.CrossValidation;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class HMMPOSTaggerTest {

    List<String[]> sentences = new ArrayList<>();
    List<PennTreebankPOS[]> labels = new ArrayList<>();
    public HMMPOSTaggerTest() {
    }
    
    /**
     * Load training data from a corpora.
     * @param dir a file object defining the top directory
     */
    public void read(String dir) {
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
                System.err.println(e);
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
    
    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testWSJ() {
        System.out.println("WSJ");
        read("PennTreebank/PennTreebank2/TAGGED/POS/WSJ");
        
        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = labels.toArray(new PennTreebankPOS[labels.size()][]);
        
        int n = x.length;
        int k = 10;

        CrossValidation cv = new CrossValidation(n, k);
        int error = 0;
        int total = 0;
        
        for (int i = 0; i < k; i++) {
            String[][] trainx = MathEx.slice(x, cv.train[i]);
            PennTreebankPOS[][] trainy = MathEx.slice(y, cv.train[i]);
            String[][] testx = MathEx.slice(x, cv.test[i]);
            PennTreebankPOS[][] testy = MathEx.slice(y, cv.test[i]);

            HMMPOSTagger tagger = HMMPOSTagger.fit(trainx, trainy);

            for (int j = 0; j < testx.length; j++) {
                PennTreebankPOS[] label = tagger.tag(testx[j]);
                total += label.length;
                for (int l = 0; l < label.length; l++) {
                    if (label[l] != testy[j][l]) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Error rate = %.2f as %d of %d\n", 100.0 * error / total, error, total);
        assertEquals(51325, error);
    }

    @Test
    public void testBrown() {
        System.out.println("BROWN");
        read("PennTreebank/PennTreebank2/TAGGED/POS/BROWN");
        
        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = labels.toArray(new PennTreebankPOS[labels.size()][]);
        
        int n = x.length;
        int k = 10;

        CrossValidation cv = new CrossValidation(n, k);
        int error = 0;
        int total = 0;
        
        for (int i = 0; i < k; i++) {
            String[][] trainx = MathEx.slice(x, cv.train[i]);
            PennTreebankPOS[][] trainy = MathEx.slice(y, cv.train[i]);
            String[][] testx = MathEx.slice(x, cv.test[i]);
            PennTreebankPOS[][] testy = MathEx.slice(y, cv.test[i]);

            HMMPOSTagger tagger = HMMPOSTagger.fit(trainx, trainy);

            for (int j = 0; j < testx.length; j++) {
                PennTreebankPOS[] label = tagger.tag(testx[j]);
                total += label.length;
                for (int l = 0; l < label.length; l++) {
                    if (label[l] != testy[j][l]) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Error rate = %.2f as %d of %d\n", 100.0 * error / total, error, total);
        assertEquals(55649, error);
    }
}
