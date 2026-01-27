/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.nlp.collocation;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import smile.nlp.SimpleCorpus;
import smile.nlp.Text;
import smile.nlp.normalizer.SimpleNormalizer;

/**
 *
 * @author Haifeng Li
 */
public class BigramTest {
    SimpleCorpus corpus = new SimpleCorpus();

    public BigramTest() {
        try {
            smile.io.Paths.getTestDataLines("text/plot.tok.gt9.5000")
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> corpus.add(new Text(line)));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testFind_Corpus_int() {
        System.out.println("k = 10");

        int k = 10;
        Bigram[] result = Bigram.of(corpus, k, 5);

        assertEquals(10, result.length);
        for (Bigram bigram : result) {
            System.out.println(bigram);
        }

        assertEquals(46, result[0].count);
        assertEquals(545.16, result[0].score, 1E-2);
        assertEquals(19, result[9].count);
        assertEquals(186.69, result[9].score, 1E-2);
    }

    @Test
    public void testFind_Corpus_double() {
        System.out.println("p = 0.0001");

        double p = 0.0001;
        Bigram[] result = Bigram.of(corpus, p, 5);

        assertEquals(63, result.length);
        for (Bigram bigram : result) {
            System.out.println(bigram);
        }

        assertEquals(46, result[0].count);
        assertEquals(545.16, result[0].score, 1E-2);
        assertEquals(6, result[62].count);
        assertEquals(10.84, result[62].score, 1E-2);
    }

    @Test
    public void testLargeMinFrequency() {
        System.out.println("Large MinFrequency");
        String content="Target could be achieved by manufacturing more electric,"
                + " hybrid cars Proposed rule could have \"significant impact\" on Europe's automakers: "
                + "industry BMW Group, Daimler, and other carmakers in the European Union would be "
                + "required to improve the fuel economy of their vehicles or increase the proportion "
                + "of electric cars they produce to meet 2030 carbon dioxide reduction targets proposed"
                + " by the European Commission Nov. 8. The commission, the EU's executive arm, said the "
                + "average private car or light van sold in the EU in 2030 should emit 30 percent less "
                + "carbon dioxide than a car or van sold in 2021--a level that European automakers say "
                + "is too ambitious. Under existing binding targets, private cars' carbon dioxide emissions"
                + " in 2021 are capped at 95 grams of carbon dioxide per kilometer traveled, while for light"
                + " vans the 2021 limit is 147 grams per kilometer. The commission's proposal was \"very aggressive\" "
                + "and the 2030 target should instead be a 20 percent reduction in average vehicle emissions, "
                + "Erik Jonnaert, secretary general of the European Automobile Manufacturers' Association, "
                + "said in a statement Nov. 8. The group speaks for carmakers including BMW Group, Daimler,"
                + "Fiat Chrysler Automobiles, and Renault Group. The proposed regulation could have \"a significant "
                + "impact on the future of Europe's automotive industry,\" because it would only succeed if a "
                + "significant switch is made to alternatively fueled vehicles, Jonnaert said. The commission's "
                + "proposed targets are in a draft EU regulation, which must be debated and agreed to by the European "
                + "Parliament and EU member countries before taking effect. Targets Measured Compliance will be "
                + "measured by calculating average emissions of new vehicles sold per manufacturer, "
                + "but manufacturers could group together so that average emissions would be calculated "
                + "across their combined vehicle fleets. The commission also said an interim target of a "
                + "15 percent carbon-dioxide reduction by 2025 should be adopted.";

        SimpleCorpus corpus = new SimpleCorpus();
        corpus.add(new Text(SimpleNormalizer.getInstance().normalize(content)));
        smile.nlp.Bigram[] bigrams = Bigram.of(corpus, 10, 3) ;
        System.out.println("Bigrams :"+ Arrays.toString(bigrams));
        assertEquals(1, bigrams.length);
    }
}