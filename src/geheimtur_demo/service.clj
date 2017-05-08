(ns geheimtur-demo.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.log :as log]
            [geheimtur.interceptor :refer [interactive guard http-basic token]]
            [geheimtur.impl.form-based :refer [default-login-handler default-logout-handler]]
            [geheimtur.impl.oauth2 :refer [authenticate-handler callback-handler]]
            [geheimtur.util.auth :as auth :refer [authenticate]]
            [geheimtur-demo.views :as views]
            [geheimtur-demo.users :refer [users]]
            [cheshire.core :refer [parse-string]]
            [ring.middleware.session.cookie :as cookie]
            [ring.util.response :as ring-resp]
            [ring.util.codec :as ring-codec]))

(defn credentials
  [_ {:keys [username password]}]
  (when-let [identity (get users username)]
    (when (= password (:password identity))
      (dissoc identity :password ))))

(defn token-credentials
  [_ token]
  (case token
    "user-secret"  (-> (get users "user") (dissoc :password))
    "admin-secret" (-> (get users "admin") (dissoc :password))
    nil))

(def access-forbidden-interceptor
  (interceptor/interceptor
   {:name  ::access-forbidden-interceptor
    :leave (fn [{:keys [response] :as ctx}]
             (let [resp (if (or
                             (= 401 (:status response))
                             (= 403 (:status response)))
                          (->
                           (views/error-page {:title "Access Forbidden" :message (:body response)})
                           (ring-resp/content-type "text/html;charset=UTF-8"))
                          response)]
               (assoc ctx :response resp)))}))

(def not-found-interceptor
  (interceptor/interceptor
   {:name  ::not-found-interceptor
    :leave (fn [{:keys [response] :as ctx}]
             (let [resp (if-not (ring-resp/response? response)
                          (->
                           (views/error-page {:title   "Not Found"
                                              :message "We are sorry, but the page you are looking for does not exist."})
                           (ring-resp/content-type "text/html;charset=UTF-8"))
                          response)]
               (assoc ctx :response resp)))}))

(defn api-error
  [context error]
  {:status 403
   :headers {}
   :body    {:error (:reason error)}})

(defn on-github-success
  [_ {:keys [identity return]}]
  (let [user {:name      (:login identity)
              :roles     #{:user}
              :full-name (:name identity)}]
    (->
     (ring-resp/redirect return)
     (authenticate user))))

(defn on-google-success
  [_ {:keys [identity return]}]
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
            :user-info-url      "https://api.github.com/user"
            ;; it is not really needed but serves as an example of how to use a custom parser
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

(def common-interceptors [(body-params/body-params) http/html-body])
(def interactive-interceptors (into common-interceptors [access-forbidden-interceptor (interactive {})]))
(def http-basic-interceptors (into common-interceptors [(http-basic "Geheimtür Demo" credentials)]))

(def routes
  #{["/"                                    :get  (conj common-interceptors `views/home-page)]
    ["/login"                               :get  (conj common-interceptors `views/login-page)]
    ["/login"                               :post (conj common-interceptors (default-login-handler {:credential-fn credentials
                                                                                                    :form-reader   identity}))]
    ["/logout"                              :get  (conj common-interceptors default-logout-handler)]
    ["/oauth.login"                         :get  (conj common-interceptors (authenticate-handler providers))]
    ["/oauth.callback"                      :get  (conj common-interceptors (callback-handler providers))]
    ["/unauthorized"                        :get  (conj common-interceptors `views/unauthorized)]
    ["/interactive"                         :get  (conj interactive-interceptors `views/interactive-index)]
    ["/interactive/restricted"              :get  (into interactive-interceptors [(guard :silent? false) `views/interactive-restricted])]
    ["/interactive/admin-restricted"        :get  (into interactive-interceptors [(guard :silent? false :roles #{:admin}) `views/interactive-admin-restricted])]
    ["/interactive/admin-restricted-hidden" :get  (into interactive-interceptors [(guard :roles #{:admin}) `views/interactive-admin-restricted-hidden])]
    ["/http-basic"                          :get  (conj http-basic-interceptors `views/http-basic-index)]
    ["/http-basic/restricted"               :get  (into http-basic-interceptors [(guard :silent? false) `views/http-basic-restricted])]
    ["/http-basic/admin-restricted"         :get  (into http-basic-interceptors [(guard :silent? false :roles #{:admin}) `views/http-basic-admin-restricted])]
    ["/http-basic/admin-restricted-hidden"  :get  (into http-basic-interceptors [(guard :roles #{:admin}) `views/http-basic-admin-restricted-hidden])]
    ["/token-based"                         :get  (conj http-basic-interceptors `views/token-based-index)]
    ["/api/restricted"                      :get  [http/json-body (token token-credentials :error-fn api-error) (guard :silent? false) `views/api-restricted]]
    ["/api/admin-restricted"                :get  [http/json-body (token token-credentials :error-fn api-error) (guard :silent? false :roles #{:admin}) `views/api-admin-restricted]]})

(def service {:env                         :prod
              ::http/routes                routes
              ::http/resource-path         "/public"
              ::http/not-found-interceptor not-found-interceptor
              ::http/type                  :jetty
              ::http/enable-session        {:cookie-name "SID"
                                            :store       (cookie/cookie-store)}
              ::http/port                  (Integer/valueOf (or (System/getenv "PORT") "8080"))})
