;   Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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

(ns smile.classification-test
  (:require [clojure.test :refer :all]
            [smile.classification :as cl])
  (:import [smile.datasets Iris]
           [smile.math MathEx]
           [smile.validation.metric Error]))

(defn- iris [] (Iris.))

(deftest decision-tree
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (cl/cart (.formula data) (.data data))
        error (Error/of (.y data) (.predict model (.data data)))]
    (println "Decision Tree training error =" error)
    (is (<= error 10))))

(deftest random-forest
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (cl/random-forest (.formula data) (.data data))
        error (Error/of (.y data) (.predict model (.data data)))]
    (println "Random Forest training error =" error)
    (is (<= error 10))))

(deftest gradient-boosting
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (cl/gbm (.formula data) (.data data))
        error (Error/of (.y data) (.predict model (.data data)))]
    (println "Gradient Boosting training error =" error)
    (is (<= error 5))))

(deftest adaboost
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (cl/adaboost (.formula data) (.data data))
        error (Error/of (.y data) (.predict model (.data data)))]
    (println "AdaBoost training error =" error)
    (is (<= error 5))))

(deftest knn
  (let [data (iris)
        model (cl/knn (.x data) (.y data) 3)
        error (Error/of (.y data) (.predict model (.x data)))]
    (println "KNN training error =" error)
    (is (<= error 10))))

(deftest lda
  (let [data (iris)
        model (cl/lda (.x data) (.y data))
        error (Error/of (.y data) (.predict model (.x data)))]
    (println "LDA training error =" error)
    (is (<= error 10))))

(deftest qda
  (let [data (iris)
        model (cl/qda (.x data) (.y data))
        error (Error/of (.y data) (.predict model (.x data)))]
    (println "QDA training error =" error)
    (is (<= error 10))))
