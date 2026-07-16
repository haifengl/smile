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

(ns smile.association-test
  (:require [clojure.test :refer :all]
            [smile.association :as a])
  (:import [smile.association FPTree]
           [java.util.stream Stream Collectors]))

(defn- make-int-array [items]
  (let [arr (java.lang.reflect.Array/newInstance Integer/TYPE (count items))]
    (doseq [[i v] (map-indexed vector items)]
      (java.lang.reflect.Array/setInt arr i v))
    arr))

(def itemsets
  (let [data [[1 3] [2] [4] [2 3 4] [2 3] [2 3] [1 2 3 4] [1 3] [1 2 3] [1 2 3]]
        arr (java.lang.reflect.Array/newInstance (type (make-int-array [1])) (count data))]
    (doseq [[i v] (map-indexed vector (map make-int-array data))]
      (java.lang.reflect.Array/set arr i v))
    arr))

(defn- stream-to-vec [stream]
  (.collect stream (Collectors/toList)))

(deftest fpgrowth
  (let [results (a/fpgrowth 3 itemsets)
        results-list (stream-to-vec results)]
    (println "FP-Growth")
    (doseq [itemset results-list]
      (println itemset))

    (is (= 8 (count results-list)))

    (is (= 3 (.support (nth results-list 0))))
    (is (= 1 (alength (.items (nth results-list 0)))))
    (is (= 4 (aget (.items (nth results-list 0)) 0)))

    (is (= 5 (.support (nth results-list 1))))
    (is (= 1 (alength (.items (nth results-list 1)))))
    (is (= 1 (aget (.items (nth results-list 1)) 0)))

    (is (= 6 (.support (nth results-list 6))))
    (is (= 2 (alength (.items (nth results-list 6)))))
    (is (= 3 (aget (.items (nth results-list 6)) 0)))
    (is (= 2 (aget (.items (nth results-list 6)) 1)))

    (is (= 8 (.support (nth results-list 7))))
    (is (= 1 (alength (.items (nth results-list 7)))))
    (is (= 3 (aget (.items (nth results-list 7)) 0)))))

(deftest arm
  (let [tree (FPTree/of 3 itemsets)
        rules (a/arm 0.5 tree)
        rules-list (stream-to-vec rules)]
    (println "ARM")
    (doseq [rule rules-list]
      (println rule))

    (is (= 9 (count rules-list)))

    (is (< 0.59 (.support (nth rules-list 0)) 0.61))
    (is (< 0.74 (.confidence (nth rules-list 0)) 0.76))
    (is (= 1 (alength (.antecedent (nth rules-list 0)))))
    (is (= 3 (aget (.antecedent (nth rules-list 0)) 0)))
    (is (= 1 (alength (.consequent (nth rules-list 0)))))
    (is (= 2 (aget (.consequent (nth rules-list 0)) 0)))

    (is (< 0.29 (.support (nth rules-list 4)) 0.31))
    (is (< 0.59 (.confidence (nth rules-list 4)) 0.61))
    (is (= 1 (alength (.antecedent (nth rules-list 4)))))
    (is (= 1 (aget (.antecedent (nth rules-list 4)) 0)))
    (is (= 1 (alength (.consequent (nth rules-list 4)))))
    (is (= 2 (aget (.consequent (nth rules-list 4)) 0)))

    (is (< 0.29 (.support (nth rules-list 8)) 0.31))
    (is (< 0.59 (.confidence (nth rules-list 8)) 0.61))
    (is (= 1 (alength (.antecedent (nth rules-list 8)))))
    (is (= 1 (aget (.antecedent (nth rules-list 8)) 0)))
    (is (= 2 (alength (.consequent (nth rules-list 8)))))
    (is (= 3 (aget (.consequent (nth rules-list 8)) 0)))
    (is (= 2 (aget (.consequent (nth rules-list 8)) 1)))))