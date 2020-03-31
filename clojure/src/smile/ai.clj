(ns smile.ai
  "Main namespace for REPL, referring to all public vars of
   other namespaces except that smile.regression :as regression."
  (:require [smile.io :refer :all]
            [smile.classification :refer :all]
            [smile.regression :as regression]
            [smile.clustering :refer :all]
            [smile.manifold :refer :all]
            [smile.mds :refer :all]))

