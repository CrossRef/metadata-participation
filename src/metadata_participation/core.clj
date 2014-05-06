(ns metadata-participation.core
  (:require [metadata-participation.templates :as templates])
  (:require [clj-http.client :as client])
  (:require [clojure.data.json :as json])
  (:require [hiccup.core :refer [html]])
  (:require [ring.util.response :refer [redirect]])
  (:require [clojure.tools.reader.edn :as edn])
 
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [org.httpkit.server :as hs]
            [compojure.core :refer [defroutes routes context GET]])
            
  (:require  [clojurewerkz.quartzite.triggers :as qt]
             [clojurewerkz.quartzite.jobs :as qj]
             [clojurewerkz.quartzite.schedule.daily-interval :as daily]
             [clojurewerkz.quartzite.schedule.calendar-interval :as cal]
             [clojurewerkz.quartzite.jobs :refer [defjob]]
             [clojurewerkz.quartzite.scheduler :as qs])  
  (:gen-class))

; Map of {feature name => publishers that participate + data}
(def features-and-publishers (atom {}))

; This is set by the main function.
(def config (atom {}))

; List of feature specs. 
(def features
  [{:name :tdm
    :fullname "Text and Data Mining"
    :description "When publishers deposit full-text links and license information researchers can access the full-text of publications to perform Text and Data Mining."
    :sort-by (fn [publisher] (max
                               (+ (-> publisher :coverage :licenses-backfile) (-> publisher :coverage :resource-links-backfile))
                               (+ (-> publisher :coverage :licenses-current) (-> publisher :coverage :resource-links-current))))
    :filter (fn [publisher] (let [flags (:flags publisher)]
                     (or (and (:deposits-licenses-backfile flags) (:deposits-resource-links-backfile flags))
                         (and (:deposits-licenses-current flags) (:deposits-resource-links-current flags)))))
    :fields [["&#8612; Back-deposits"
                [[:coverage :licenses-backfile] "Licenses"]
                [[:coverage :resource-links-backfile] "Full text links"]]
             ["&#8614; Current deposits"
                [[:coverage :licenses-current] "Licenses"]
                [[:coverage :resource-links-current] "Full text links"]]]}
   
   {:name :orcid
    :fullname "ORCID Author Deposits"
    :description "ORCID IDs identify authors of publications."
    :sort-by (fn [publisher] (max
                               (-> publisher :coverage :orcids-backfile)
                               (-> publisher :coverage :orcids-current)))
    
    :filter (fn [publisher] (let [flags (:flags publisher)]
                     (or (:deposits-orcids-backfile flags)
                         (:deposits-orcids-current flags))))
    
    :fields [["&#8612; Back-deposits"
                [[:coverage :orcids-backfile] "ORCID Author Deposits"]]
             ["&#8614; Current deposits"
                [[:coverage :orcids-current] "ORCID Author Deposits"]]]}

   {:name :funding
    :fullname "Funding Information"
    :description "Publishers can deposit Funding Information so people can see which funding bodies paid for the research behind a publication."
    :sort-by (fn [publisher] (max
                               (-> publisher :coverage :funders-backfile)
                               (-> publisher :coverage :funders-current)))
    
    :filter (fn [publisher] (let [flags (:flags publisher)]
                     (or (:deposits-funders-backfile flags)
                         (:deposits-funders-current flags))))
    
    :fields [["&#8612; Back-deposits"
                [[:coverage :orcids-backfile] "Funding Information"]]
             ["&#8614; Current deposits"
                [[:coverage :orcids-current] "Funding Information"]]]}])

(def features-by-name (apply merge (map (fn [feature] {(:name feature) feature}) features)))

(def api-endpoint "http://api.crossref.org/members")
(def api-page-size 500)

(defn stars [value]  (cond
    (> value 0.8) :three-stars
    (> value 0.4) :two-stars
    (> value 0) :one-star
    :else :zero-stars))

(defn get-num-publishers
  []
  (let [response (client/get (str api-endpoint "?" (client/generate-query-string {:rows 0})))
        response-json (json/read-str (:body response) :key-fn keyword)
        total-results (-> response-json :message :total-results)]
  total-results))

(defn get-publisher-page [page]
  (let [response (client/get (str api-endpoint "?" (client/generate-query-string {:rows api-page-size :offset (* api-page-size page)})))
        response-json (json/read-str (:body response) :key-fn keyword)
        items (-> response-json :message :items)]
  items))

(defn get-publishers []
  (let [num-publishers (get-num-publishers)
        num-pages (quot num-publishers api-page-size)
        results (map get-publisher-page (range 0 (inc num-pages)))
        all-results (apply concat results)]
    all-results))

(defn filter-publishers-for-feature
  "Filter a list of Publishers for a given feature."
  [feature publishers]
  (let [filtered (filter (:filter feature) publishers)
        ; sort-f (:sort-by feature)
        ; sorted (reverse (sort-by sort-f filtered))        
        sort-f #(:primary-name %)
        sorted (sort-by sort-f filtered)]
    sorted))

(defn process-publisher-feature
  "Take a Publisher's metadata and return info for the given feature."
  [feature publisher]
  (let [processed-by-fields (map (fn [[col-name & rows]]
                                   [col-name 
                                    (map (fn [[field-path field-name]] [field-name (get-in publisher field-path)])
                                         rows)])
                                  (:fields feature))]
    processed-by-fields))

(defn decorate-publisher-feature [feature publisher]
  {:name (:primary-name publisher)
   :metadata publisher
   :feature-data (process-publisher-feature feature publisher)
   :feature feature})

(defn decorate-publishers-for-all-features
  "For a list of publishers, return a map of {feature name => publishers for feature}"
  [publishers]
  (apply merge 
  (map 
    (fn [feature]
      (let [feature-name (:name feature)
            ; Filter and sort publishers who participate in this feature.
            publishers-filtered (filter-publishers-for-feature feature publishers)
            
            ; Add data for the feature and sort.
            publishers-decorated (map #(decorate-publisher-feature feature %) publishers-filtered)]
        
        {feature-name publishers-decorated}))
    features)))


(defjob UpdateJob
  [ctx]
    (let [publishers (get-publishers)
          decorated-publishers-for-all-features (decorate-publishers-for-all-features publishers)]
  (reset! features-and-publishers decorated-publishers-for-all-features)))

(defn features-handler []
  (html (templates/features (:url-prefix @config)
        (for [feature features]
          (list
            [:h2 [:a {:href (str (:url-prefix @config) "/features/" (name (:name feature)))} (:fullname feature)]] [:p (:description feature)] )))))

(defn template-stars-html 
  [stars]
  (html (cond
    (= stars :three-stars) [:span "&#x2605;&#x2605;&#x2605;"]
    (= stars :two-stars) [:span "&#x2605;&#x2605;&#x2606;"] 
    (= stars :one-star) [:span "&#x2605;&#x2606;&#x2606;"]
    :else [:span "&#x2606;&#x2606;&#x2606;"]
    )))

(defn member-table-html
  [member-feature]
  (html [:table
             (for [column (:feature-data member-feature)]
               (list [:tr [:td {:class :feature-time-period} (first column)]
                          (for [label-value-pairs (rest column)]
                            (list (for [[label value] label-value-pairs] (list [:td {:class :feature-label} label] [:td {:class :feature-value} (template-stars-html (stars value))]))))]))]))

(defn feature-handler [feature-name]
  (let [feature-name (keyword feature-name)
        feature (feature-name features-by-name)
        publishers-decorated @features-and-publishers
        publisher-for-feature (get publishers-decorated feature-name)]
          
      (html (templates/feature (:url-prefix @config)
              (:fullname feature)
              (:description feature)
              (for [publisher publisher-for-feature]
                (list 
                  [:h2 [:a {:href (str (:url-prefix @config) "/member/" (-> publisher :metadata :id))} (:name publisher)]]            
                  (member-table-html publisher)))))))
      


(defn member-handler [member-id]
  (let [id (Integer/parseInt member-id)
        response (client/get (str api-endpoint "/" member-id))
        response-json (json/read-str (:body response) :key-fn keyword)
        publisher-data (-> response-json :message)
        publisher-per-feature (map #(decorate-publisher-feature % publisher-data) features)]

        (html (templates/member (:url-prefix @config) (:primary-name publisher-data)
          (for [publisher-feature publisher-per-feature]
           (list 
             [:h2 (-> publisher-feature :feature :fullname)]
               (member-table-html publisher-feature)))))))

(defroutes the-routes
  (GET "/" [] (redirect "/features"))
  (GET "/features" [] (features-handler))
  (GET ["/features/:feature" :feature #".*"] [feature] (feature-handler feature))
  (GET ["/member/:id" :id #"\d*"] [id] (member-handler id))
  (route/resources "/"))

(def app
  (-> the-routes
      handler/site))

(defn -main
  [& args]
    (let [the-config (edn/read-string (slurp "config.edn"))]
      (reset! config the-config)
    
      (qs/initialize)
      (qs/start)
      
      (let [job (qj/build
                 (qj/of-type UpdateJob)
                 (qj/with-identity (qj/key "jobs.update")))
            trigger (qt/build
                     (qt/with-identity (qt/key "triggers.update"))
                     (qt/start-now)
                     (qt/with-schedule (cal/schedule (cal/with-interval-in-minutes 20))))]
        (qs/schedule job trigger))
        (hs/run-server app {:port (:port @config)})))
