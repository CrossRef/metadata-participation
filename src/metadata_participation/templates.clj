(ns metadata-participation.templates)

(defn base [url-prefix body]

[:html {:lang "en"}
      [:head
        [:meta {:charset "utf-8"} ]
        [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"} ]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"} ]
        [:meta {:name "description" :content "Landing page for CrossRef Apps"} ]
        [:title "CrossRef Apps"]
        [:link {:href (str url-prefix "/css/bootstrap.min.css") :rel "stylesheet"}]
        [:link {:href (str url-prefix "/css/participation.css") :rel "stylesheet"}]
      ]

      [:body
        [:div {:class "container"}
          body
      
          [:div {:class "footer"}
            [:p "&copy; CrossRef 2014"]]]]])


(defn features [url-prefix features-list]
  (base url-prefix (list
          [:div {:class "header"}
            [:img {:src (str url-prefix "/img/crossref-logo.png") :alt "CrossRef Logo"}]]

          [:div {:class "jumbotron"}
            [:h1 "CrossRef Metadata Participation"]
            [:p {:class "lead"} "CrossRef is the place where Publishers deposit Metadata. Different kinds of metadata allow you to different things. Here you can see which metadata each publisher deposits and what you can use it for."]]

          [:div {:class "row"}
            [:div {:class "col-lg-6"}
            features-list
          ]])))

(defn member [url-prefix member-name features-list]
  (base url-prefix (list
          [:div {:class "header"}
            [:img {:src (str url-prefix "/img/crossref-logo.png") :alt "CrossRef Logo"}]]

          [:a {:href (str url-prefix "/features")} "&laquo; Back to list of features"]
            
          [:div {:class "jumbotron"}
            [:h1 member-name]
            features-list])))


(defn feature [url-prefix feature-name feature-description members-list]
  (base url-prefix (list
      [:div {:class "header"}
        [:img {:src (str url-prefix "/img/crossref-logo.png") :alt "CrossRef Logo"}]
      ]
      [:a {:href (str url-prefix "/features")} "&laquo; Back to list of features"]

      [:div {:class "jumbotron"}
        [:h1 feature-name]
        [:p {:class :lead} feature-description]
      ]
      
      members-list)))