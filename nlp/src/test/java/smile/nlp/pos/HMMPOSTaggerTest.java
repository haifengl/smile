/*
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
 */

package smile.nlp.pos;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.util.Paths;
import smile.validation.CrossValidation;
import smile.validation.Bag;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class HMMPOSTaggerTest {

    public HMMPOSTaggerTest() {

    }
    
    /**
     * Load training data from a corpora.
     * @param dir a file object defining the top directory
     */
    public void read(Path dir, List<String[]> sentences, List<PennTreebankPOS[]> tags) throws IOException {
        List<File> files = new ArrayList<>();
        walkin(dir, files);

        for (File file : files) {
            try {
                FileInputStream stream = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line;
                List<String> sentence = new ArrayList<>();
                List<PennTreebankPOS> tag = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        if (!sentence.isEmpty()) {
                            sentences.add(sentence.toArray(new String[0]));
                            tags.add(tag.toArray(new PennTreebankPOS[0]));
                            sentence.clear();
                            tag.clear();
                        }
                    } else if (!line.startsWith("===") && !line.startsWith("*x*")) {
                        String[] words = line.split("\\s");
                        for (String word : words) {
                            String[] w = word.split("/");
                            if (w.length == 2) {
                                sentence.add(w[0]);
                                
                                int index = w[1].indexOf('|');
                                String pos = index == -1 ? w[1] : w[1].substring(0, index);
                                if (pos.equals("PRP$R")) pos = "PRP$";
                                if (pos.equals("JJSS")) pos = "JJS";
                                tag.add(PennTreebankPOS.getValue(pos));
                            }
                        }
                    }
                }
                
                if (!sentence.isEmpty()) {
                    sentences.add(sentence.toArray(new String[0]));
                    tags.add(tag.toArray(new PennTreebankPOS[0]));
                    sentence.clear();
                    tag.clear();
                }
                
                reader.close();
            } catch (Exception ex) {
                System.err.println(ex.getMessage());
            }
        }
    }

    /**  
     * Recursive function to descend into the directory tree and find all the files
     * that end with ".POS"
     * @param dir a file object defining the top directory
     **/
    public static void walkin(Path dir, List<File> files) throws IOException {
        String pattern = ".POS";
        Files.newDirectoryStream(dir).forEach(path -> {
            File file = path.toFile();
            if (file.isDirectory()) {
                try {
                    walkin(path, files);
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
            } else {
                if (file.getName().endsWith(pattern)) {
                    files.add(file);
                }
            }
        });
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
    public void testWSJ() throws IOException {
        System.out.println("WSJ");

        MathEx.setSeed(19650218); // to get repeatable results.
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> tags = new ArrayList<>();
        read(Paths.getTestData("nlp/PennTreebank/PennTreebank2/TAGGED/POS/WSJ"), sentences, tags);

        // Data is not available
        if (sentences.isEmpty()) return;

        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = tags.toArray(new PennTreebankPOS[tags.size()][]);
        
        int n = x.length;
        int k = 10;
        int error = 0;
        int total = 0;

        Bag[] bags = CrossValidation.of(n, k);
        for (int i = 0; i < k; i++) {
            String[][] trainx = MathEx.slice(x, bags[i].samples);
            PennTreebankPOS[][] trainy = MathEx.slice(y, bags[i].samples);
            String[][] testx = MathEx.slice(x, bags[i].oob);
            PennTreebankPOS[][] testy = MathEx.slice(y, bags[i].oob);

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

        System.out.format("Error rate = %.2f%% as %d of %d\n", 100.0 * error / total, error, total);
        assertEquals(51325, error);
    }

    @Test
    public void testBrown() throws IOException {
        System.out.println("BROWN");

        MathEx.setSeed(19650218); // to get repeatable results.
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> tags = new ArrayList<>();
        read(Paths.getTestData("nlp/PennTreebank/PennTreebank2/TAGGED/POS/BROWN"), sentences, tags);

        // Data is not available
        if (sentences.isEmpty()) return;

        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = tags.toArray(new PennTreebankPOS[tags.size()][]);
        
        int n = x.length;
        int k = 10;
        int error = 0;
        int total = 0;

        Bag[] bags = CrossValidation.of(n, k);
        for (int i = 0; i < k; i++) {
            String[][] trainx = MathEx.slice(x, bags[i].samples);
            PennTreebankPOS[][] trainy = MathEx.slice(y, bags[i].samples);
            String[][] testx = MathEx.slice(x, bags[i].oob);
            PennTreebankPOS[][] testy = MathEx.slice(y, bags[i].oob);

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

        System.out.format("Error rate = %.2f%% as %d of %d\n", 100.0 * error / total, error, total);
        assertEquals(55589, error);
    }
}
