(ns smile.io
  "I/O utilities"
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

