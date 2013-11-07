(ns geheimtur-demo.views
  (:require [geheimtur.util.auth :refer [get-identity]]
            [geheimtur-demo.users :refer [users]]
            [ring.util.response :as ring-resp]
            [hiccup.page :as h]
            [hiccup.element :as e]))

(def head
  [:head
   [:title "Geheimtür Demo"]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
   [:link {:href "/css/bootstrap.min.css" :media "screen" :rel "stylesheet" :type "text/css"}]
   "<!--[if lt IE 9]>"
   [:script {:src "/js/html5shiv.js"}]
   [:script {:src "/js/respond.min.js"}]
   "<![endif]-->"])

(defn navbar
  [user]
  [:nav {:class "navbar navbar-default" :role "navigation"}
   [:div {:class "navbar-header"}
    [:button {:type "button" :class "navbar-toggle" :data-toggle "collapse" :data-target ".navbar-ext1-collapse"}
     [:span {:class "sr-only"} "Toggle navigation"]
     [:span {:class "icon-bar"}]
     [:span {:class "icon-bar"}]
     [:span {:class "icon-bar"}]]
    [:a {:class "navbar-brand" :href "/"} "Geheimtür Demo"]]
   [:div {:class "collapse navbar-collapse navbar-ex1-collapse"}
    [:ul {:class "nav navbar-nav"}
     [:li
      [:a {:href "/form-based"} "Form-Based"]]
     [:li
      [:a {:href "/http-basic"} "HTTP-Basic"]]]
    (when-not (nil? user)
      [:div {:class "navbar-right"}
       [:p {:class "navbar-text"}
        "Signed in as " [:strong (:full-name user)]]
       [:a {:href "/logout" :class "btn btn-primary navbar-btn"}
        "Logout"]])]])

(defn body
  [user & content]
  [:body
   (navbar user)
   [:div {:class "container"}
    [:div {:class "row"}
     content]]
   [:script {:src "//code.jquery.com/jquery.js"}]
   [:script {:src "/js/bootstrap.min.js"}]])

(defn login-form
  [action has-error]
  [:div {:class "row"}
   [:div {:class "col-lg-6 col-lg-offset-3"}
    (when has-error
      [:div {:class "alert alert-danger alert-dismissable"}
       [:button {:type "button" :class "close" :data-dismiss "alert" :aria-hidden "true"} "&times;"]
       "Wrong username and password combination."])
    [:form {:method "POST" :action action :accept-charset "UTF-8"}
     [:fieldset
      [:legend "Sign in"]
      [:div {:class "form-group"}
       [:label {:for "username" :class "control-label hidden"} "Username"]
       [:input {:type "text" :class "form-control" :id "username" :name "username" :placeholder "Username" :autocomplete "off"}]]
      [:div {:class "form-group"}
       [:label {:for "password" :class "control-label hidden"} "Password"]
       [:input {:type "password" :class "form-control" :id "password" :name "password" :placeholder "Password"}]]
      [:div {:class "form-group"}
       [:button {:type "submit" :class "btn btn-default btn-block"} "Sign in"]]]]]])

(defn error-page
  [context]
  (ring-resp/response
   (h/html5 head (body (:user context)
                       [:h2 (:title context)]
                       [:p (:message context)]))))

(defn home-page
  [request]
  (ring-resp/response
   (h/html5 head (body (get-identity request)
                       [:div {:class "col-lg-8 col-lg-offset-2"}
                        [:p [:a {:href "https://github.com/propan/geheimtur"} "geheimtur"]
                         " - is a collection of interceptors and functions that simplify addition of authentication/authorization to your Pedestal application. "
                         "At this moment, it provides support for form-based and http-basic authentications."]
                        [:p "The source code of this application is available on " [:a {:href "https://github.com/propan/geheimtur-demo"} "GitHub"]]
                        [:h3 "Credentials"]
                        [:p "All demos accept the following username/password combinations:"]
                        [:ul
                         [:li [:code "user/password"] " - associated with *user* role"]
                         [:li [:code "admin/password"] " - assosiated with *admin* role"]]]))))

(defn form-based-index
  [request]
  (ring-resp/response
   (h/html5 head (body (get-identity request)
                       [:div {:class "col-lg-8 col-lg-offset-2"}
                        [:h2 "Form-based authentication"]
                        [:p "This part of the application demonstrated the form-based authentication flow.
                       Below you can find links that lead to the pages with different access level.
                       Geheimtur allows to to hide pages from anonymous users or users that have not enough access rights. "
                         [:strong "admin-restricted-hidden"] " - is an example of such a page, instead of getting \"Access Forbidden\" page "
                         "or being prompted their password, users are shown 404 \"Page Not Found\" in response."]
                        [:p "You can proceed the following ways:"
                         [:ul
                          [:li
                           [:a {:href "/form-based/restricted"} "restricted"] " - open for any authenticated user"]
                          [:li
                           [:a {:href "/form-based/admin-restricted"} "admin-restricted"] " - open for administrators only"]
                          [:li
                           [:a {:href "/form-based/admin-restricted-hidden"} "admin-restricted-hidden"] " - open for administrators and hidden for the rest of the world"]]]]))))

(defn form-based-restricted
  [request]
  (let [identity (get-identity request)]
    (ring-resp/response (h/html5 head (body identity
                                            [:div {:class "col-lg-8 col-lg-offset-2"}
                                             [:h2 "Restricted area"]
                                             [:p "Hello, " (:name identity) "! We are happy you found a way to reach this page. Only real users can achieve such an amazing page!"]])))))

(defn form-based-admin-restricted
  [request]
  (let [identity (get-identity request)]
    (ring-resp/response (h/html5 head (body identity
                                            [:div {:class "col-lg-8 col-lg-offset-2"}
                                             [:h2 "Administrator Only Area"]
                                             [:p "Here is what we know about you: " identity]])))))

(defn form-based-admin-restricted-hidden
  [request]
  (ring-resp/response (h/html5 head (body (get-identity request)
                                          [:div {:class "col-lg-8 col-lg-offset-2"}
                                           [:h2 "Administrator Only Area"]
                                           [:p "This is our most secret area and we wanted to make it really special, so here's the list of all system users:"]
                                           [:ul
                                            (for [rec users
                                                  :let [u (second rec)]]
                                              [:li [:strong (:full-name u)] ": " (:name u) "/" (:password u)])]]))))

(defn login-page
  [{:keys [params] :as request}]
  (let [action    (if-let [return (:return params)]
                    (str "/login?return=" return)
                    "/login")
        has-error (contains? params :error)]
    (ring-resp/response (h/html5 head (body (get-identity request) (login-form action has-error))))))

(defn http-basic-index
  [request]
  (ring-resp/response (h/html5 head (body (get-identity request)
                                          [:div {:class "col-lg-8 col-lg-offset-2"}
                                           [:h2 "HTTP Basic Authentication"]
                                           [:p "This part of the application demonstrated the HTTP Basic authentication flow. 
                                               That flow provides the same authorization options as the form-based flow, but instead of redirecting 
                                               users to the authentication page, they are prompted for HTTP Basic credentials."]
                                           [:p "If you try to access " [:a {:href "/http-basic/restricted"} "restricted area"] ", you will be prompted (by your browser) to enter valid credentials."]
                                           [:div {:class "alert alert-warning"}
                                            "Some browsers save HTTP Basic credentials for the duration of the session and resend them automatically, so logging out might not work as expected."]]))))

(defn http-basic-restricted
  [request]
  (let [identity (get-identity request)]
    (ring-resp/response (h/html5 head (body identity
                                            [:div {:class "col-lg-8 col-lg-offset-2"}
                                             [:h2 "HTTP Basic restricted area"]
                                             [:p "Congratulations, " (:full-name identity) ", you successfuly bypassed HTTP Basic authentication!"]])))))
