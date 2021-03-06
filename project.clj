(defproject crossref-metadata-participation "0.1.0-SNAPSHOT"
  :description "Show Publishers' participation in various metadata features."
  :url "http://apps.crossref.org/participation"
  :plugins [[lein-ring "0.8.10"] [lein-daemon "0.5.4"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.0"]
                 [org.clojure/data.json "0.2.4"]
                 [hiccup "1.0.5"]
                 [lein-ring "0.8.10"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.1.6"]
                 [http-kit "2.1.10"]
                 [clojurewerkz/quartzite "1.1.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 
                 ]
  :main ^:skip-aot metadata-participation.core
  :target-path "target/%s"
  :ring {:handler metadata-participation.core/app}
  :profiles {:uberjar {:aot :all}}
  :daemon {:metadata-participation {:ns metadata-participation.core :pidfile "metadata-participation.pid"}})
