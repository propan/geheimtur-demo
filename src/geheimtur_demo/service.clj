(ns geheimtur-demo.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.http.ring-middlewares :as middlewares]
              [io.pedestal.service.interceptor :as interceptor :refer [defon-response]]
              [io.pedestal.service.log :as log]
              [selmer.parser :as selmer]
              [selmer.util :refer :all]
              [geheimtur.interceptor :refer [form-based guard http-basic]]
              [geheimtur.util.auth :as auth :refer [authenticate]]
              [geheimtur-demo.views :as views]
              [ring.middleware.session.cookie :as cookie]
              [ring.util.response :as ring-resp]))

(def users {"user" {:name "user" :password "password" :roles #{:user}}
            "admin" {:name "admin" :password "password" :roles #{:admin}}})

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
    (-> (selmer/render-file "error.html" {:title "Access Forbidden" :message (:body response)})
      (ring-resp/response)
      (ring-resp/content-type "text/html;charset=UTF-8"))
    response))

(defon-response not-found-interceptor
  [response]
  (if-not (ring-resp/response? response)
    (-> (selmer/render-file "error.html" {:title "Not Found"
                                          :message "We could not find the page you are looking for.."})
      (ring-resp/response )
      (ring-resp/content-type "text/html;charset=UTF-8"))
    response))

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     #_(http-basic "Geheimtür Demo" credentials)]
     ["/login" {:get views/login-handler}]
     ["/form-based" {:get views/form-based-index} ^:interceptors []
      ["/restricted" {:get views/form-based-restricted} ^:interceptors [(guard :silent? false)]]
      ["/user-restricted" {:get views/form-based-user-restricted} ^:interceptors [(guard :silent? false :roles #{:user :admin})]]
      ["/admin-restricted" {:get views/form-based-admin-restricted} ^:interceptors [(guard :roles #{:admin})]]]
     ["/about" {:get views/about-page} ^:interceptors [(guard :silent? false)]]]]])

(defn merge-seq
  [xs p? merge-fn]
  (reduce
    #(if (p? %2)
       (merge-fn %1 %2)
       (conj %1 %2))
    (empty xs) xs))

(defn replace-seq
  [xs p? ys]
  (merge-seq xs p? (fn [zs z] (reduce conj zs ys))))

(defn insert-before-seq
  [xs p? ys]
  (merge-seq xs p? #(conj (reduce conj %1 ys) %2)))

(defn name-eq
  [name]
  (fn [interceptor]
    (= (:name interceptor) name)))

(interceptor/definterceptor session-interceptor
  (middlewares/session {:cookie-name "SID"
                        :store (cookie/cookie-store)}))

(def service (-> {:env :prod
                  ::bootstrap/routes routes
                  ::bootstrap/resource-path "/public"
                  ::bootstrap/type :jetty
                  ::bootstrap/port 8080}
               (bootstrap/default-interceptors)
               ;; replace default not-found interceptor and add 403 interceptor
               (update-in [::bootstrap/interceptors ]
                 (fn [interceptors]
                   (replace-seq interceptors (name-eq ::bootstrap/not-found )
                     [not-found-interceptor access-forbidden-interceptor])))
               ;; should be inserted before router
               (update-in [::bootstrap/interceptors ]
                 (fn [interceptors]
                   (insert-before-seq interceptors (name-eq ::route/router )
                     [(body-params/body-params) session-interceptor (form-based {:credential-fn credentials})])))
               ))

