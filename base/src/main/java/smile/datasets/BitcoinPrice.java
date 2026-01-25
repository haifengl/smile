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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.io.Read;
import smile.io.Paths;

/**
 * Bitcoin Price history on a daily basis from April-28th, 2013 to Feb-20th,
 * 2018.
 *
 * <pre>
 *   Date,          Open,    High,    Low,     Close,   Volume,         Market Cap
 *   "Feb 20, 2018",11231.80,11958.50,11231.80,11403.70,"9,926,540,000","189,536,000,000"
 *   "Feb 19, 2018",10552.60,11273.80,10513.20,11225.30,"7,652,090,000","178,055,000,000"
 *   "Feb 18, 2018",11123.40,11349.80,10326.00,10551.80,"8,744,010,000","187,663,000,000"
 *   ...
 * </pre>
 *
 * <h2>References</h2>
 * <ol>
 * <li><a href="https://www.kaggle.com/sudalairajkumar/cryptocurrencypricehistory">Cryptocurrency Historical Prices</a></li>
 * <li><a href="https://arxiv.org/pdf/1904.05315.pdf">Bitcoin Price Prediction: An ARIMA Approach</a></li>
 * </ol>
 *
 * @param data data frame.
 * @author Haifeng Li
 */
public record BitcoinPrice(DataFrame data) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public BitcoinPrice() throws IOException {
        this(Paths.getTestData("timeseries/bitcoin_price.csv"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     */
    public BitcoinPrice(Path path) throws IOException {
        this(load(path));
    }

    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create()
                .setTrim(true)
                .setHeader().setSkipHeaderRecord(true)
                .get();
        return Read.csv(path, format);
    }

    /**
     * Returns the closing price series, which are commonly believed to
     * be non-stationary.
     * @return the closing price series.
     */
    public double[] price() {
        return data.column("Close").toDoubleArray();
    }

    /**
     * Returns the log of closing price series, which are commonly believed to
     * be unit-root non-stationary.
     * @return the log of closing price series.
     */
    public double[] logPrice() {
        var price = data.column("Close").toDoubleArray();
        for (int i = 0; i < price.length; i++) {
            price[i] = Math.log(price[i]);
        }

        return price;
    }
}
