(ns geheimtur-demo.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [geheimtur-demo.service :as service]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(deftest home-page-test
  (is (.contains
       (:body (response-for service :get "/"))
       "Geheimtür Demo"))
  (is (=
       (:headers (response-for service :get "/"))
       {"Content-Type"              "text/html;charset=UTF-8"
        "Strict-Transport-Security" "max-age=31536000; includeSubdomains"
        "X-Frame-Options"           "DENY"
        "X-Content-Type-Options"    "nosniff"
        "X-XSS-Protection"          "1; mode=block"})))
