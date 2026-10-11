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
package smile.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;

class SparkTest {
    /** Minimize footprint for GitHub Actions. */
    static final SparkConf conf = new SparkConf()
            .setMaster("local[1]") // Use a single thread to reduce memory overhead
            .set("spark.driver.memory", "512m")
            .set("spark.executor.memory", "512m")
            .set("spark.sql.shuffle.partitions", "2") // Prevents high-memory shuffles
            .set("spark.ui.enabled", "false");

    /** Creates spark session with the shared config. */
    static SparkSession createSession(String appName) {
        return SparkSession.builder()
                .config(conf)
                .appName(appName)
                .getOrCreate();
    }
}
