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

(ns smile.regression-test
  (:require [clojure.test :refer :all]
            [smile.regression :as r])
  (:import [smile.datasets AutoMPG]
           [smile.math MathEx]
           [smile.validation.metric RMSE]))

(defn- auto-mpg [] (AutoMPG.))

(deftest regression-tree
  (MathEx/setSeed 19650218)
  (let [data (auto-mpg)
        model (r/cart (.formula data) (.data data) 20 100 5)
        rmse (RMSE/of (.y data) (.predict model (.data data)))]
    (println "Regression Tree training RMSE =" rmse)
    (is (< rmse 5.0))))

(deftest random-forest
  (MathEx/setSeed 19650218)
  (let [data (auto-mpg)
        model (r/random-forest (.formula data) (.data data))
        rmse (RMSE/of (.y data) (.predict model (.data data)))]
    (println "Random Forest training RMSE =" rmse)
    (is (< rmse 5.0))))

(deftest gradient-boosting
  (MathEx/setSeed 19650218)
  (let [data (auto-mpg)
        model (r/gbm (.formula data) (.data data))
        rmse (RMSE/of (.y data) (.predict model (.data data)))]
    (println "Gradient Boosting training RMSE =" rmse)
    (is (< rmse 5.0))))
