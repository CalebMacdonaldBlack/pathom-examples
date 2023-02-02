(ns com.calebmacdonaldblack.cyclone.pathom.plugin
  (:require
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.plugin :as p.plugin]
    [datascript.core :as d]))

(p.plugin/defplugin transact-entity
  {::pcr/wrap-merge-attribute
   (fn [original]
     (fn [{:com.example.db/keys [schema] :as env} {:com.example/keys [id] :as out} k v]
       (let [env (update env :com.example/db
                   (fn [db]
                     (d/db-with (or db (d/empty-db schema))
                                [{:com.example/id id k v}])))]
         (original env out k v))))
   ::pcr/wrap-root-run-graph!
   (fn [original]
     (fn [{:com.example.db/keys [schema] :as env} ast-or-graph entity-tree*]
       (let [env (update env :com.example/db
                   (fn [db]
                     (d/db-with (or db (d/empty-db schema))
                                [@entity-tree*])))]
         (original env ast-or-graph entity-tree*))))})
