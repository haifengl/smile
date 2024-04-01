(defproject org.clojars.haifengl/smile "3.1.0"
  :description "Smile - Statistical Machine Intelligence and Learning Engine"
  :url "https://haifengl.github.io"
  :scm {:name "git"
        :url "https://github.com/haifengl/smile"}
  :license {:name "GNU General Public License, Version 3"
            :url "https://opensource.org/licenses/GPL-3.0"}
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [com.github.haifengl/smile-core "3.1.0"]]
  :plugins [[lein-codox "0.10.8"]]
  :codox {:doc-files ["README.md"]
          :output-path "../doc/api/clojure"}
  :jvm-opts ["-XX:MaxRAMPercentage=75.0"
             "-XX:+UseStringDeduplication"
             "-XX:+UseG1GC"]
  :repl-options {:init-ns smile.ai})
