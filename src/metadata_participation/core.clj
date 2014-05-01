(ns metadata-participation.core
  (:require [clj-http.client :as client])
  (:require [clojure.data.json :as json])
  (:require [hiccup.core :refer [html]])
 
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

; List of feature specs. 
(def features
  [{:name :tdm
    :fullname "Text and Data Mining"
    :sort-by (fn [publisher] (max
                               (+ (-> publisher :coverage :licenses-backfile) (-> publisher :coverage :resource-links-backfile))
                               (+ (-> publisher :coverage :licenses-current) (-> publisher :coverage :resource-links-current))))
    :filter (fn [publisher] (let [flags (:flags publisher)]
                     (or (and (:deposits-licenses-backfile flags) (:deposits-resource-links-backfile flags))
                         (and (:deposits-licenses-current flags) (:deposits-resource-links-current flags)))))
    :fields [["Back-deposits"
                [[:coverage :licenses-backfile] "Licenses for back-files"]
                [[:coverage :resource-links-backfile] "Full text resource links for back-files"]]
             ["Current deposits"
                [[:coverage :licenses-current] "Licenses for current content"]
                [[:coverage :resource-links-current] "Full text resource links for current content"]]]}
   
   {:name :orcid
    :fullname "ORCID Author Deposits"
    :sort-by (fn [publisher] (max
                               (-> publisher :coverage :orcids-backfile)
                               (-> publisher :coverage :orcids-current)))
    
    :filter (fn [publisher] (let [flags (:flags publisher)]
                     (or (:deposits-orcids-backfile flags)
                         (:deposits-orcids-current flags))))
    
    :fields [["Back-deposits"
                [[:coverage :orcids-backfile] "ORCID Author Deposits"]]
             ["Current deposits"
                [[:coverage :orcids-current] "ORCID Author Deposits"]]]}

   {:name :funding
    :fullname "Funding Information"
    :sort-by (fn [publisher] (max
                               (-> publisher :coverage :funders-backfile)
                               (-> publisher :coverage :funders-current)))
    
    :filter (fn [publisher] (let [flags (:flags publisher)]
                     (or (:deposits-funders-backfile flags)
                         (:deposits-funders-current flags))))
    
    :fields [["Back-deposits"
                [[:coverage :orcids-backfile] "Funding Information Deposits"]]
             ["Current deposits"
                [[:coverage :orcids-current] "Funding Information Deposits"]]]}])

(def features-by-name (apply merge (map (fn [feature] {(:name feature) feature}) features)))

(def api-endpoint "http://api.crossref.org/members")
(def api-page-size 500)

(defn stars [value]
  (cond
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
   :feature (process-publisher-feature feature publisher)})

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
  (prn "Update job")
    (let [publishers (get-publishers)
          decorated-publishers-for-all-features (decorate-publishers-for-all-features publishers)]
  (reset! features-and-publishers decorated-publishers-for-all-features)))

(defn features-handler []
  (html [:h1 "Features"]
        (for [feature features]
          (list
            [:h2 [:a {:href (str "/features/" (name (:name feature)))} (:fullname feature)]] ))))

(defn feature-handler [feature-name]
  (let [feature-name (keyword feature-name)
        feature (feature-name features-by-name)
        publishers-decorated @features-and-publishers
        publisher-for-feature (get publishers-decorated feature-name)]
    (html
      [:h1 (:fullname feature)]
      (for [publisher publisher-for-feature]
        (list 
            [:h2 (:name publisher)]
            [:table
             (for [column (:feature publisher)]
               (list [:tr [:td (first column)]
                          (for [label-value-pairs (rest column)]
                            (list (for [[label value] label-value-pairs] (list [:td label] [:td (stars value)]))))]))])))))

(defroutes the-routes
  (GET "/features" [] (features-handler))
  (GET ["/features/:feature" :feature #".*"] [feature] (feature-handler feature)))

(def app
  (-> the-routes
      handler/site))

(defn -main
  [& args]
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
    (hs/run-server app {:port 9876}))