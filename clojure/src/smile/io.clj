;   Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
;
;   Smile is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Lesser General Public License as
;   published by the Free Software Foundation, either version 3 of
;   the License, or (at your option) any later version.
;
;   Smile is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;   GNU Lesser General Public License for more details.
;
;   You should have received a copy of the GNU Lesser General Public License
;   along with Smile.  If not, see <https://www.gnu.org/licenses/>.

(ns smile.io
  "I/O utilities"
  {:author "Haifeng Li"}
  (:import [smile.io Read]
           [smile.data DataFrame]
           [org.apache.commons.csv CSVFormat]))

(defn read-jdbc
  "Reads a JDBC query result to a data frame."
  [rs]
  (DataFrame/of rs))

(defn read-csv
  "Reads a CSV file."
  ([path] (read-csv path \, true))
  ([path delimiter header]
  (let [base (.withDelimiter CSVFormat/DEFAULT delimiter)
        format (if header (.withFirstRecordAsHeader base) base)]
     (Read/csv path format))))

(defn read-arff
  "Reads an ARFF file."
  [path]
  (Read/arff path))
    
(defn read-json
  "Reads a JSON file."
  [path]
  (Read/json path))
      
(defn read-sas
  "Reads a SAS7BDAT file."
  [path]
  (Read/sas path))

(defn read-arrow
  "Reads an Apache Arrow file."
  [path]
  (Read/arrow path))

(defn read-avro
  "Reads an Apache Avro file."
  [path schema]
  (Read/avro path schema))

(defn read-parquet
  "Reads an Apache Parquet file."
  [path]
  (Read/parquet path))

(defn read-libsvm
  "Reads a libsvm file."
  [path]
  (Read/libsvm path))

