;
;   SMILE is free software: you can redistribute it and/or modify it
;   under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   SMILE is distributed in the hope that it will be useful, but
;   WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with SMILE. If not, see <https://www.gnu.org/licenses/>.

(ns smile.read-test
  (:require [clojure.test :refer :all]
            [smile.io :as io])
  (:import [java.nio.file Paths]))

(defn- test-data [path]
  (let [base (Paths/get "../base/src/test/resources/data" (into-array String []))]
    (.toString (.resolve base path))))

(deftest read-arff
  (let [path (test-data "weka/weather.nominal.arff")
        df (io/read-arff path)]
    (println "arff")
    (println df)
    (is (= 14 (.nrow df)))
    (is (= 5 (.ncol df)))))

(deftest read-csv-zip
  (let [path (test-data "usps/zip.train")
        df (io/read-csv path " " false)]
    (println "csv zip")
    (println df)
    (is (= 7291 (.nrow df)))
    (is (= 257 (.ncol df)))))