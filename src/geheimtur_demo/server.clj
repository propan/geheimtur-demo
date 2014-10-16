(ns geheimtur-demo.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as server]
            [geheimtur-demo.service :as service]
            [io.pedestal.service-tools.dev :as dev]))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (-> (dev/init service/service)
      (server/create-server)
      (server/start)))

;; To implement your own server, copy io.pedestal.service-tools.server and
;; customize it.

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (-> service/service
      (server/create-server)
      (server/start)))

;; Fns for use with io.pedestal.servlet.ClojureVarServlet

(defn servlet-init [this config]
  (server/servlet-init this config))

(defn servlet-destroy [this]
  (server/servlet-destroy this))

(defn servlet-service [this servlet-req servlet-resp]
  (server/servlet-service this servlet-req servlet-resp))
