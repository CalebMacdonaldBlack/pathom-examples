(ns com.calebmacdonaldblack.cyclone.example
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [datascript.core :as d]))

;; input -> ref -> lookup -> output
(pco/defresolver person []
  {::pco/input  [:com.example/id]
   ::pco/output [:com.example.person/given-name
                 :com.example.person/surname]})

(pco/defresolver person-lookup-ref
  [{:com.example/keys [id]}]
  {:com.example/lookup-ref
   [:com.example/id id]})

(pco/defresolver
  person-given-name
  [{:com.example/keys [db]}
   {:com.example/keys [lookup-ref]}]
  {:com.example.person/given-name
   (-> (d/pull db [:com.example.person/given-name] lookup-ref)
       :com.example.person/given-name
       (or ::pco/unknown-value))})



(pco/defresolver
  person-spouse
  [{:com.example/keys [db] :as env}
   {:com.example/keys [lookup-ref]}]
  {::pco/output [:com.example/spouse]}
  {:com.example/spouse
   ;(psm/smart-map env)
   (:com.example/spouse
     (d/pull db [{:com.example/spouse [:com.example/id]}] lookup-ref))})

(pco/defresolver
  person-spouse-reverse-lookup
  [{:com.example/keys [db] :as env}
   {:com.example/keys [lookup-ref]}]
  {::pco/output [:com.example/_spouse]}
  {:com.example/_spouse
   ;(psm/smart-map env)
   (first
     (:com.example/_spouse
       (d/pull db [{:com.example/_spouse [:com.example/id]}] lookup-ref)))})

(def new-db
  (-> (d/empty-db {:com.example/id     {:db/unique :db.unique/identity}
                   :com.example/spouse {:db/valueType   :db.type/ref
                                        :db/cardinality :db.cardinality/one}})
      (d/db-with [{:db/id                         "caleb"
                   :com.example/id                "caleb"
                   :com.example.person/given-name "Caleb Ian Geoffrey"
                   :com.example/spouse            "erin"}
                  {:db/id                         "erin"
                   :com.example/id                "erin"
                   :com.example.person/given-name "Erin Therese"
                   :com.example/spouse            "caleb"}])))

(def env
  (-> (pci/register [person-lookup-ref
                     person-given-name
                     person-spouse
                     person-spouse-reverse-lookup])
      (psm/with-wrap-nested? false)
      (assoc :com.example/db new-db)))

(deftest derive-person-surname-from-family-test
  (is (= {:com.example/lookup-ref [:com.example/id "caleb"]}
         (p.eql/process
           env
           {:com.example/id "caleb"}
           [:com.example/lookup-ref])))

  (is (= {:com.example.person/given-name "Caleb Ian Geoffrey"
          :com.example/spouse            {:com.example.person/given-name "Erin Therese"
                                          :com.example/_spouse           {:com.example/id "caleb"}
                                          :com.example/id                "erin"
                                          :com.example/lookup-ref        [:com.example/id
                                                                          "erin"]}}
         (p.eql/process
           env
           {:com.example/id "caleb"}
           [:com.example.person/given-name
            {:com.example/spouse [:com.example.person/given-name
                                  :com.example/_spouse]}]))))
