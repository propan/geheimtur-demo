(ns geheimtur-demo.views
  (:require [ring.util.response :as ring-resp]
            [selmer.parser :as selmer]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s" (clojure-version))))

(selmer/set-resource-path! "/home/propan/S/geheimtur-demo/resources/templates/")

(defn home-page
  [request]
  (ring-resp/response (selmer/render-file "index.html" {})))

(defn form-based-index
  [request]
  (ring-resp/response (selmer/render-file "form-based/index.html" {})))

(defn form-based-restricted
  [request]
  (ring-resp/response (selmer/render-file "form-based/restricted.html" {})))

(defn form-based-user-restricted
  [request]
  (ring-resp/response (selmer/render-file "form-based/user-restricted.html" {})))

(defn form-based-admin-restricted
  [request]
  (ring-resp/response (selmer/render-file "form-based/admin-restricted.html" {})))

(defn login-handler
  [request]
  (let [action (if-let [return (get-in request [:params :return ])]
                 (str "/login?return=" return)
                 "/login")]
    (ring-resp/response (selmer/render-file "form-based/login.html" {:action action}))))