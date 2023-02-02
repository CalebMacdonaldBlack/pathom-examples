(ns com.example.core18
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [com.wsscode.misc.coll :as coll]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.planner :as pcp]
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.format.eql :as pf.eql]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.pathom3.plugin :as p.plugin]
    [datascript.core :as d]))

(comment
  (let [schema {:a {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (d/transact! conn [{:db/id -1
                        :name  "Maksim"
                        :age   45
                        :aka   ["Max Otto von Stierlitz", "Jack Ryan"]}])
    (d/q '[:find ?n ?a
           :where [?e :aka "Max Otto von Stierlitz"]
           [?e :name ?n]
           [?e :age ?a]]
         @conn)))

(p.plugin/defplugin entity-cache-wrapper
  (let [tid (atom 0)]
    {
     ::pf.eql/wrap-map-select-entry
     (fn [original]
       (fn [env source ast]
         (let [new-source (assoc source :tid (swap! tid dec))]
           (original env new-source ast))))}))

(pco/defresolver lower->upper
  [_ {:keys [lower]}]
  {::pco/input  [:lower]
   ::pco/output [:upper]}
  {:upper (str/upper-case lower)})

(let [env (-> (pci/register [lower->upper])
              (p.plugin/register entity-cache-wrapper)
              (update ::pf.eql/map-select-include coll/sconj :tid))]
  (p.eql/process env {:lower "foobar"} [:upper]))

(def family-db
  (pbir/constantly-resolver
    :com.example.family/db
    {1 {:com.example.family/surname "Smith"}}))

(def family-by-id
  (pbir/attribute-table-resolver
    :com.example.family/db
    :com.example/id
    [:com.example.family/surname]))

(def env
  (pci/register
    [family-db
     family-by-id]))
