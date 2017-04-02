(defproject geheimtur-demo "0.0.1-SNAPSHOT"
  :description "Geheimtür Demo Application"
  :url "https://github.com/propan/geheimtur-demo"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.2"]
                 [io.pedestal/pedestal.jetty "0.5.2"]
                 [hiccup "1.0.5"]
                 [geheimtur "0.3.2-SNAPSHOT"]
                 [cheshire "5.7.0"]
                 [ch.qos.logback/logback-classic "1.1.8" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.22"]
                 [org.slf4j/jcl-over-slf4j "1.7.22"]
                 [org.slf4j/log4j-over-slf4j "1.7.22"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "geheimtur-demo.server/run-dev"]}
  :main ^{:skip-aot true} geheimtur-demo.server)
