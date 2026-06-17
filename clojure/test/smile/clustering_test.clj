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

(ns smile.clustering-test
  (:require [clojure.test :refer :all]
            [smile.clustering :as c])
  (:import [smile.datasets Iris]
           [smile.math MathEx]
           [smile.math.distance EuclideanDistance]
           [smile.validation.metric RandIndex]))

(defn- iris [] (Iris.))

(deftest kmeans
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (c/kmeans (.x data) 3)
        ri (RandIndex/of (.y data) (.group model))]
    (println "K-Means rand index =" ri)
    (is (> ri 0.7))))

(deftest kmeans-runs
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (c/kmeans (.x data) 3 100 1e-4 4)
        ri (RandIndex/of (.y data) (.group model))]
    (println "K-Means (runs) rand index =" ri)
    (is (> ri 0.7))))

(deftest gmeans
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (c/gmeans (.x data) 6)
        ri (RandIndex/of (.y data) (.group model))]
    (println "G-Means rand index =" ri)
    (is (> ri 0.5))))

(deftest kmedoids
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (c/kmedoids (.x data) (EuclideanDistance.) 3)
        ri (RandIndex/of (.y data) (.group model))]
    (println "K-Medoids rand index =" ri)
    (is (> ri 0.7))))

(deftest kmedoids-runs
  (MathEx/setSeed 19650218)
  (let [data (iris)
        model (c/kmedoids (.x data) (EuclideanDistance.) 3 3)
        ri (RandIndex/of (.y data) (.group model))]
    (println "K-Medoids (runs) rand index =" ri)
    (is (> ri 0.7))))
