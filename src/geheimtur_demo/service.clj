(ns geheimtur-demo.service
    (:require [io.pedestal.http :as bootstrap]
              [io.pedestal.http.route :as route]
              [io.pedestal.http.body-params :as body-params]
              [io.pedestal.http.route.definition :refer [defroutes]]
              [io.pedestal.http.ring-middlewares :as middlewares]
              [io.pedestal.interceptor :as interceptor :refer [defon-response]]
              [io.pedestal.log :as log]
              [geheimtur.interceptor :refer [interactive guard http-basic]]
              [geheimtur.impl.form-based :refer [default-login-handler default-logout-handler]]
              [geheimtur.impl.oauth2 :refer [authenticate-handler callback-handler]]
              [geheimtur.util.auth :as auth :refer [authenticate]]
              [geheimtur-demo.views :as views]
              [geheimtur-demo.users :refer [users]]
              [cheshire.core :refer [parse-string]]
              [ring.middleware.session.cookie :as cookie]
              [ring.util.response :as ring-resp]))

(defn credentials
  [username password]
  (when-let [identity (get users username)]
    (when (= password (:password identity))
      (dissoc identity :password ))))

(defon-response access-forbidden-interceptor
  [response]
  (if (or
        (= 401 (:status response))
        (= 403 (:status response)))
    (->
     (views/error-page {:title "Access Forbidden" :message (:body response)})
     (ring-resp/content-type "text/html;charset=UTF-8"))
    response))

(defon-response not-found-interceptor
  [response]
  (if-not (ring-resp/response? response)
    (->
     (views/error-page {:title   "Not Found"
                        :message "We are sorry, but the page you are looking for does not exist."})
     (ring-resp/content-type "text/html;charset=UTF-8"))
    response))

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

(def providers
  {:github {:auth-url           "https://github.com/login/oauth/authorize"
            :client-id          (or (System/getenv "github.client_id") "client-id")
            :client-secret      (or (System/getenv "github.client_secret") "client-secret")
            :scope              "user:email"
            :token-url          "https://github.com/login/oauth/access_token"
            :user-info-url      "https://api.github.com/user"
            :user-info-parse-fn #(parse-string % true)
            :on-success-handler on-github-success}})

(def oath-handler
  (authenticate-handler providers))

(def oath-callback-handler
  (callback-handler providers))

(interceptor/definterceptor session-interceptor
  (middlewares/session {:cookie-name "SID"
                        :store (cookie/cookie-store)}))

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     session-interceptor]
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
                  ::bootstrap/port (Integer/valueOf (or (System/getenv "PORT") "8080"))}
               (bootstrap/default-interceptors)))

