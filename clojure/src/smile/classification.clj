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

(ns smile.classification
  "Classification Algorithms"
  {:author "Haifeng Li"}
  (:import [smile.classification KNN LogisticRegression Maxent
                                 MLP RBFNetwork SVM DecisionTree
                                 RandomForest GradientTreeBoost
                                 AdaBoost FLD LDA QDA RDA]
           [smile.base.cart SplitRule]))

(defn knn
  "K-nearest neighbor classifier."
  ([x y] (knn x y 1))
  ([x y k] (KNN/fit x y k))
  ([x y k distance] (KNN/fit x y k distance)))

(defn logit
  "Logistic regression."
  ([x y] (logit x y 0.0 1E-5 500))
  ([x y lambda tol max-iter] (LogisticRegression/fit x y lambda tol max-iter)))

(defn maxent
  "Maximum Entropy classifier."
  ([x y p] (maxent x y p 0.1 1E-5 500))
  ([x y p lambda tol max-iter] (Maxent/fit x y p lambda tol max-iter)))

(defn mlp
  "Multilayer perceptron neural network."
  ([x y builders] (mlp x y builders 10 0.1 0.0 0.0))
  ([x y builders epochs eta alpha lambda]
   (let [net (MLP. (.length (aget x 0)) builders)]
     ((.setLearningRate net eta)
      (.setMomentum net alpha)
      (.setWeightDecay net lambda)
      (dotimes [i epochs] (.update net x, y))
      net))))

(defn rbfnet
  "Radial basis function networks."
  ([x y neurons] (rbfnet x y neurons false))
  ([x y neurons normalized] (RBFNetwork/fit x y neurons normalized)))

(defn svm
  "Support vector machine."
  ([x y kernel C] (svm x y kernel C 1E-3))
  ([x y kernel C tol] (SVM/fit x y kernel C tol)))

(defn cart
  "Decision tree."
  ([formula data] (cart formula data SplitRule/GINI 20 0 5))
  ([formula data split-rule max-depth max-nodes node-size]
   (DecisionTree/fit formula data split-rule max-depth max-nodes node-size)))

(defn random-forest
  "Random forest."
  ([formula data] (random-forest formula data 500 0 SplitRule/GINI 20 500 5 1.0))
  ([formula data ntrees mtry split-rule max-depth max-nodes node-size subsample]
   (RandomForest/fit formula data ntrees mtry split-rule max-depth max-nodes node-size subsample)))

(defn gbm 
  "Gradient boosted classification trees."
  ([formula data] (gbm formula data 500 20 6 5 0.05 0.7))
  ([formula data ntrees max-depth max-nodes node-size shrinkage subsample]
   (GradientTreeBoost/fit formula data ntrees max-depth max-nodes node-size shrinkage subsample)))

(defn adaboost
  "Adaptive Boosting."
  ([formula data] (adaboost formula data 500 20 6 1))
  ([formula data ntrees max-depth max-nodes node-size]
   (AdaBoost/fit formula data ntrees max-depth max-nodes node-size)))

(defn fld
  "Fisher's linear discriminant."
  ([x y] (fld x y -1 0.0001))
  ([x y L tol]
   (FLD/fit x y L tol)))

(defn lda
  "Linear discriminant analysis."
  ([x y] (lda x y nil 0.0001))
  ([x y priori tol]
   (LDA/fit x y priori tol)))

(defn qda
  "Quadratic discriminant analysis."
  ([x y] (qda x y nil 0.0001))
  ([x y priori tol]
   (QDA/fit x y priori tol)))

(defn rda
  "Regularized discriminant analysis."
  ([x y alpha] (rda x y alpha nil 0.0001))
  ([x y  alpha priori tol]
   (RDA/fit x y alpha priori tol)))
