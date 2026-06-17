(defproject org.clojars.haifengl/smile "6.2.0"
  :description "Smile - Statistical Machine Intelligence and Learning Engine"
  :url "https://haifengl.github.io"
  :scm {:name "git"
        :url "https://github.com/haifengl/smile"}
  :license {:name "GNU General Public License, Version 3"
            :url "https://opensource.org/licenses/GPL-3.0"}
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [com.github.haifengl/smile-core "6.2.0"]]
  :plugins [[lein-codox "0.10.8"]]
  :codox {:doc-files ["README.md"]
          :project {:name "Smile - Clojure" :description "Copyright © 2010-2026 Haifeng Li. All rights reserved. Use is subject to license terms."}
          :output-path "../doc/api/clojure"}
  :jvm-opts ["-XX:MaxRAMPercentage=75.0"
             "-XX:+UseStringDeduplication"
             "-XX:+UseG1GC"]
  ;; Tests load datasets from the repo's shared test resources via the
  ;; "smile.home" property (lein runs with the clojure/ directory as CWD).
  :profiles {:test {:jvm-opts ["-Dsmile.home=../base/src/test/resources"]}}
  :repl-options {:init-ns smile.ai})
