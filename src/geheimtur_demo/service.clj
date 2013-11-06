(ns geheimtur-demo.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.http.ring-middlewares :as middlewares]
              [io.pedestal.service.interceptor :as interceptor :refer [defon-response]]
              [io.pedestal.service.log :as log]
              [geheimtur.interceptor :refer [form-based guard http-basic]]
              [geheimtur.impl.form-based :refer [default-login-handler default-logout-handler]]
              [geheimtur.util.auth :as auth :refer [authenticate]]
              [geheimtur-demo.views :as views]
              [geheimtur-demo.users :refer [users]]
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

(interceptor/definterceptor session-interceptor
  (middlewares/session {:cookie-name "SID"
                        :store (cookie/cookie-store)}))

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     session-interceptor
                     access-forbidden-interceptor
                     #_(http-basic "Geheimtür Demo" credentials)]
     ["/login" {:get views/login-page :post login-post-handler}]
     ["/logout" {:get default-logout-handler}]
     ["/form-based" {:get views/form-based-index} ^:interceptors [(form-based {})]
      ["/restricted" {:get views/form-based-restricted} ^:interceptors [(guard :silent? false)]]
      ["/admin-restricted" {:get views/form-based-admin-restricted} ^:interceptors [(guard :silent? false :roles #{:admin})]]
      ["/admin-restricted-hidden" {:get views/form-based-admin-restricted-hidden} ^:interceptors [(guard :roles #{:admin})]]]
     ["/about" {:get views/about-page} ^:interceptors [(guard :silent? false)]]]]])

(def service (-> {:env :prod
                  ::bootstrap/routes routes
                  ::bootstrap/resource-path "/public"
                  ::bootstrap/not-found-interceptor not-found-interceptor
                  ::bootstrap/type :jetty
                  ::bootstrap/port 8080}
               (bootstrap/default-interceptors)))


