(ns geheimtur-demo.users)

(def users {"user" {:name "user" :password "password" :roles #{:user} :full-name "Bobby Briggs"}
            "admin" {:name "admin" :password "password" :roles #{:admin :agent} :full-name "Dale Cooper"}})
