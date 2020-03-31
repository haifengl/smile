(ns smile.regression
  "Regression Analysis"
  (:import [smile.regression OLS RidgeRegression LASSO MLP RBFNetwork SVR
                             RegressionTree RandomForest GradientTreeBoost
                             GaussianProcessRegression]
           [smile.base.cart Loss]))

(defn ols
  "Ordinary least squares."
  ([formula data] (ols formula data "qr" true true))
  ([formula data method, stderr recursive] (OLS/fit formula data method stderr recursive)))

(defn ridge
  "Ridge Regression."
  [formula data lambda] (RidgeRegression/fit formula data lambda))

(defn lasso
  "Least absolute shrinkage and selection operator."
  ([formula data lambda] (lasso formula data lambda 0.001 5000))
  ([formula data lambda tol max-iter] (LASSO/fit formula data lambda tol max-iter)))

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

(defn svr
  "Support vector regression."
  ([x y kernel eps C] (svr x y kernel eps C 1E-3))
  ([x y kernel eps C tol] (SVR/fit x y kernel eps C tol)))

(defn cart
  "Regression tree."
  ([formula data] (cart formula data 20 0 5))
  ([formula data max-depth max-nodes node-size]
   (RegressionTree/fit formula data max-depth max-nodes node-size)))

(defn random-forest
  "Random forest."
  ([formula data] (random-forest formula data 500 0 20 500 5 1.0))
  ([formula data ntrees mtry max-depth max-nodes node-size subsample]
   (RandomForest/fit formula data ntrees mtry max-depth max-nodes node-size subsample)))

(defn gbm 
  "Gradient boosted classification trees."
  ([formula data] (gbm formula data (Loss/lad) 500 20 6 5 0.05 0.7))
  ([formula data loss ntrees max-depth max-nodes node-size shrinkage subsample]
   (GradientTreeBoost/fit formula data loss ntrees max-depth max-nodes node-size shrinkage subsample)))

(defn gpr
  "Gaussian process."
  [x y kernel lambda] (GaussianProcessRegression/fit x y kernel lambda))

(defn gpr-approx
  "Approximate Gaussian process with a subset of regressors."
  [x y t kernel lambda] (GaussianProcessRegression/fit x y t kernel lambda))

(defn gpr-nystrom
  "Approximate Gaussian process with Nystrom approximation of kernel matrix."
  [x y t kernel lambda] (GaussianProcessRegression/fit x y t kernel lambda))

