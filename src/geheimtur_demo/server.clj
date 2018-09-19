(ns geheimtur-demo.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]
            [geheimtur-demo.service :as service]))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with production configuration
      (merge {:env                     :dev
              ;; do not block thread that starts web server
              ::server/join?           false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::server/routes          #(route/expand-routes (deref #'service/routes))
              ;; all origins are allowed in dev mode
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))

;; To implement your own server, copy io.pedestal.service-tools.server and
;; customize it.

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (let [config service/service]
    (log/info :msg "Starting HTTP server" :port (::server/port config))
    (-> config
        (assoc ::server/host "0.0.0.0")
        (server/create-server)
        (server/start))))

;; Fns for use with io.pedestal.servlet.ClojureVarServlet

(defn servlet-init [this config]
  (server/servlet-init this config))

(defn servlet-destroy [this]
  (server/servlet-destroy this))

(defn servlet-service [this servlet-req servlet-resp]
  (server/servlet-service this servlet-req servlet-resp))
