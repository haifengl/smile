(defproject org.clojars.haifengl/smile "2.5.3"
  :description "Smile - Statistical Machine Intelligence and Learning Engine"
  :url "https://haifengl.github.io"
  :scm {:name "git"
        :url "https://github.com/haifengl/smile"}
  :license {:name "GNU Lesser General Public License, Version 3"
            :url "https://opensource.org/licenses/LGPL-3.0"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.github.haifengl/smile-core "2.5.3"]
                 [com.github.haifengl/smile-io "2.5.3"]]
  :plugins [[lein-codox "0.10.7"]]
  :jvm-opts ["-XX:MaxRAMPercentage=75.0"
             "-XX:+UseStringDeduplication"
             "-XX:+UseG1GC"]
  :repl-options {:init-ns smile.ai})
