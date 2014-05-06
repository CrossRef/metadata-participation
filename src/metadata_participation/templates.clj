(ns metadata-participation.templates)

(defn base [body]

[:html {:lang "en"}
      [:head
        [:meta {:charset "utf-8"} ]
        [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"} ]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"} ]
        [:meta {:name "description" :content "Landing page for CrossRef Apps"} ]
        [:title "CrossRef Apps"]
        [:link {:href "/css/bootstrap.min.css" :rel "stylesheet"}]
        [:link {:href "/css/participation.css" :rel "stylesheet"}]
      ]

      [:body
        [:div {:class "container"}
          body
      
          [:div {:class "footer"}
            [:p "&copy; CrossRef 2014"]]]]])

(defn home []
  (base (list
          [:div {:class "header"}
            [:img {:src "/img/crossref-logo.png" :alt "CrossRef Logo"}]
          ]

          [:div {:class "jumbotron"}
            [:h1 "CrossRef Metadata Participation"]
            [:p {:class "lead"} "CrossRef is the place where Publishers deposit Metadata. Different kinds of metadata allow you to different things. Here you can see which metadata each publisher deposits and what you can use it for."]
          ]

          [:div {:class "row"}
            [:div {:class "col-lg-6"}
            [:h2 {:href "/features/tdm"} "Text and Data Mining"]
            [:p "You can perform Text and Data Mining on DOIs when publishers deposit license and full-text metadata. This allows you to automatically identify the license under which a publication is made available and download the full-text."]
          ]])))

(defn features [features-list]
  (base (list
          [:div {:class "header"}
            [:img {:src "/img/crossref-logo.png" :alt "CrossRef Logo"}]]

          [:div {:class "jumbotron"}
            [:h1 "CrossRef Metadata Participation"]
            [:p {:class "lead"} "CrossRef is the place where Publishers deposit Metadata. Different kinds of metadata allow you to different things. Here you can see which metadata each publisher deposits and what you can use it for."]]

          [:div {:class "row"}
            [:div {:class "col-lg-6"}
            features-list
          ]])))

(defn member [member-name features-list]
  (base (list
          [:div {:class "header"}
            [:img {:src "/img/crossref-logo.png" :alt "CrossRef Logo"}]]

          [:div {:class "jumbotron"}
            [:h1 member-name]
            features-list])))


(defn feature [feature-name feature-description members-list]
  (base (list
      [:div {:class "header"}
        [:img {:src "/img/crossref-logo.png" :alt "CrossRef Logo"}]
      ]

      [:div {:class "jumbotron"}
        [:h1 feature-name]
        [:p {:class :lead} feature-description]
      ]
      
      members-list)))