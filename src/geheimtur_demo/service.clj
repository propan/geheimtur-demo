(ns geheimtur-demo.service
    (:require [io.pedestal.http :as bootstrap]
              [io.pedestal.http.route :as route]
              [io.pedestal.http.body-params :as body-params]
              [io.pedestal.http.route.definition :refer [defroutes]]
              [io.pedestal.interceptor.helpers :as interceptor :refer [on-response]]
              [io.pedestal.log :as log]
              [geheimtur.interceptor :refer [interactive guard http-basic]]
              [geheimtur.impl.form-based :refer [default-login-handler default-logout-handler]]
              [geheimtur.impl.oauth2 :refer [authenticate-handler callback-handler]]
              [geheimtur.util.auth :as auth :refer [authenticate]]
              [geheimtur-demo.views :as views]
              [geheimtur-demo.users :refer [users]]
              [cheshire.core :refer [parse-string]]
              [clojure.walk :refer [keywordize-keys]]
              [ring.middleware.session.cookie :as cookie]
              [ring.util.response :as ring-resp]
              [ring.util.codec :as ring-codec]))

(defn credentials
  [username password]
  (when-let [identity (get users username)]
    (when (= password (:password identity))
      (dissoc identity :password ))))

(def access-forbidden-interceptor
  (on-response
   ::access-forbidden-interceptor
   (fn [response]
     (if (or
          (= 401 (:status response))
          (= 403 (:status response)))
       (->
        (views/error-page {:title "Access Forbidden" :message (:body response)})
        (ring-resp/content-type "text/html;charset=UTF-8"))
       response))))

(def not-found-interceptor
  (on-response
   ::not-found-interceptor
   (fn [response]
     (if-not (ring-resp/response? response)
       (->
        (views/error-page {:title   "Not Found"
                           :message "We are sorry, but the page you are looking for does not exist."})
        (ring-resp/content-type "text/html;charset=UTF-8"))
       response))))

(def login-post-handler
  (default-login-handler {:credential-fn credentials}))

(defn on-github-success
  [{:keys [identity return]}]
  (let [user {:name      (:login identity)
              :roles     #{:user}
              :full-name (:name identity)}]
    (->
     (ring-resp/redirect return)
     (authenticate user))))

(defn on-google-success
  [{:keys [identity return]}]
  (let [user {:name      (:displayName identity)
              :roles     #{:user}
              :full-name (:displayName identity)}]
    (->
     (ring-resp/redirect return)
     (authenticate user))))

(def providers
  {:github {:auth-url           "https://github.com/login/oauth/authorize"
            :client-id          (or (System/getenv "github_client_id") "client-id")
            :client-secret      (or (System/getenv "github_client_secret") "client-secret")
            :scope              "user:email"
            :token-url          "https://github.com/login/oauth/access_token"
            ;; use a custom function until (and if) https://github.com/dakrone/clj-http/pull/264 is merged
            :token-parse-fn     #(-> % :body ring-codec/form-decode keywordize-keys)
            :user-info-url      "https://api.github.com/user"
            ;; it is not really need but serves as an example of how to use a custom parser
            :user-info-parse-fn #(-> % :body (parse-string true))
            :on-success-handler on-github-success}
   :google {:auth-url           "https://accounts.google.com/o/oauth2/auth"
            :client-id          (or (System/getenv "google_client_id") "client-id")
            :client-secret      (or (System/getenv "google_client_secret") "client-secret")
            :callback-uri       "http://geheimtur.herokuapp.com/oauth.callback"
            :scope              "profile email"
            :token-url          "https://accounts.google.com/o/oauth2/token"
            :user-info-url      "https://www.googleapis.com/plus/v1/people/me"
            :on-success-handler on-google-success}})

(def oath-handler
  (authenticate-handler providers))

(def oath-callback-handler
  (callback-handler providers))

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body]
     ["/login" {:get views/login-page :post login-post-handler}]
     ["/logout" {:get default-logout-handler}]
     ["/oauth.login" {:get oath-handler}]
     ["/oauth.callback" {:get oath-callback-handler}]
     ["/unauthorized" {:get views/unauthorized}]
     ["/interactive" {:get views/interactive-index} ^:interceptors [access-forbidden-interceptor (interactive {})]
      ["/restricted" {:get views/interactive-restricted} ^:interceptors [(guard :silent? false)]]
      ["/admin-restricted" {:get views/interactive-admin-restricted} ^:interceptors [(guard :silent? false :roles #{:admin})]]
      ["/admin-restricted-hidden" {:get views/interactive-admin-restricted-hidden} ^:interceptors [(guard :roles #{:admin})]]]
     ["/http-basic" {:get views/http-basic-index} ^:interceptors [(http-basic "Geheimtür Demo" credentials)]
      ["/restricted" {:get views/http-basic-restricted} ^:interceptors [(guard :silent? false)]]
      ["/admin-restricted" {:get views/http-basic-admin-restricted} ^:interceptors [(guard :silent? false :roles #{:admin})]]
      ["/admin-restricted-hidden" {:get views/http-basic-admin-restricted-hidden} ^:interceptors [(guard :roles #{:admin})]]]]]])

(def service (-> {:env :prod
                  ::bootstrap/routes routes
                  ::bootstrap/resource-path "/public"
                  ::bootstrap/not-found-interceptor not-found-interceptor
                  ::bootstrap/type :jetty
                  ::bootstrap/enable-session {:cookie-name "SID"
                                              :store (cookie/cookie-store)}
                  ::bootstrap/port (Integer/valueOf (or (System/getenv "PORT") "8080"))}
               (bootstrap/default-interceptors)))

