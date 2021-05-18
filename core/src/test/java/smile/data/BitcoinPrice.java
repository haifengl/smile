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

package smile.data;

import org.apache.commons.csv.CSVFormat;
import smile.io.Read;
import smile.timeseries.TimeSeries;
import smile.util.Paths;

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
 * <li>https://www.kaggle.com/sudalairajkumar/cryptocurrencypricehistory</li>
 * <li>https://arxiv.org/pdf/1904.05315.pdf</li>
 * </ol>
 *
 * @author rayeaster
 */
public class BitcoinPrice {

    public static DataFrame data;
    // The price series are commonly believed to be nonstationary.
    public static double[] price;
    // The log price series, log(price), is unit-root nonstationary,
    // which can be treated as an ARIMA process.
    public static double[] logPrice;
    // The log return series, log(p_t) - log(p_t-1), is stationary.
    public static double[] logReturn;

    static {
        try {
            data = Read.csv(Paths.getTestData("timeseries/bitcoin_price.csv"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim());

            price = data.doubleVector("Close").toDoubleArray();
            logPrice = new double[price.length];
            for (int i = 0; i < price.length; i++) {
                logPrice[i] = Math.log(price[i]);
            }
            logReturn = TimeSeries.diff(logPrice, 1);
        } catch (Exception ex) {
            System.err.println("Failed to load 'BitcoinPrice': " + ex);
            System.exit(-1);
        }
    }
}
