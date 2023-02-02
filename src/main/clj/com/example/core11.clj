(ns com.example.core11
  (:require
    [pyramid.core :as py]
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql])
  (:import (clojure.lang MapEntry)))

(declare ->pyramid-db)

;; Mock a database
(def house-database
  {:db/id           0
   :house/address   "1 House Street"
   :house/occupants [{:occupant/first-name "Sam"}
                     {:occupant/first-name "Kerry"}
                     {:occupant/first-name "John"}]})

;; Aggregate parent and child attributes into single child attribute
(pco/defresolver occupant-summary
  [{:occupant/keys [first-name] {:house/keys [address occupant-count]} :occupant/house}]
  {::pco/input [:occupant/first-name {:occupant/house [:house/address :house/occupant-count]}]}
  (prn "Occupant Summary")
  {:occupant/summary (str first-name " lives at " address " with " (dec occupant-count) " others.")})

;; Fetch number of occupants in house
(pco/defresolver occupant-count
  [{:house/keys [occupants]}]
  {::pco/output [:house/occupant-count]}
  (prn "Occupant Count")
  {:house/occupant-count (count occupants)})

(pco/defresolver house
  [{:py/keys [db]} {:db/keys [id]}]
  {::pco/output [:house/address {:house/occupants [:db/id]}]}
  (prn "House")
  (let [lookup-ref [:db/id id]]
    (get (py/pull db [lookup-ref])
         lookup-ref)))

(pco/defresolver occupant
  [{:py/keys [db]} {:db/keys [id]}]
  {::pco/output [:occupant/first-name {:occupant/house [:db/id]}]}
  (prn "Occupant")
  (let [lookup-ref [:db/id id]
        {[house]        :house/_occupants
         :occupant/keys [first-name]} (get (py/pull db [lookup-ref]) lookup-ref)]
    {:occupant/first-name first-name
     :occupant/house      house}))

(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street with 2 others."}
                            {:occupant/summary "Kerry lives at 1 House Street with 2 others."}
                            {:occupant/summary "John lives at 1 House Street with 2 others."}]}
         (-> (pci/register [occupant-summary occupant-count house occupant])
             (merge {:py/db (->pyramid-db house-database)})
             (p.eql/process {:db/id 0}
                            [{:house/occupants [:occupant/summary]}])))))

;; Sanity check unit test
(deftest house-test
  (is (= {:house/address   "1 House Street"
          :house/occupants [{:db/id 1} {:db/id 2} {:db/id 3}]}
         (-> (pci/register [house])
             (merge {:py/db (->pyramid-db house-database)})
             (p.eql/process
               {:db/id 0}
               [:house/address :house/occupants])))))

;; Sanity check unit test
(deftest occupant-test
  (is (= {:occupant/first-name "Sam"
          :occupant/house      {:db/id 0}}
         (-> (pci/register [occupant])
             (merge {:py/db (->pyramid-db house-database)})
             (p.eql/process {:db/id 1}
                            [:occupant/first-name :occupant/house])))))



(defn- index-for-pyramid
  "Traverse potentially nested data-structure, add db/id idents & index datomic style reverse lookup attributes"
  ([form] (let [*py-id   (atom -1)
                py-id-fn #(swap! *py-id inc)]
            (index-for-pyramid py-id-fn form nil)))
  ([py-id-fn form [parent-k parent-id :as parent]]
   (cond

     (and (map-entry? form) (coll? form))
     (let [[k v] form]
       (MapEntry. k (index-for-pyramid py-id-fn v [(keyword (namespace k) (str "_" (name k))) parent-id])))

     (map? form)
     (let [id (py-id-fn)]
       (cond->> (assoc form :db/id id)
         true (into {} (map #(index-for-pyramid py-id-fn % [nil id])))
         parent (merge {parent-k [{:db/id parent-id}]})))

     (coll? form)
     (mapv #(index-for-pyramid py-id-fn % parent) form)

     :else
     form)))

(defn ->pyramid-db [input]
  (let []
    (-> input
        (index-for-pyramid)
        (vector)
        (py/db))))

(deftest ->pyramid-db-test
  (testing "A query"
    (is (= {:db/id {0 {:db/id           0
                       :house/address   "1 House Street"
                       :house/occupants [[:db/id 1] [:db/id 2] [:db/id 3]]}
                    1 {:db/id               1
                       :house/_occupants    [[:db/id 0]]
                       :occupant/first-name "Sam"}
                    2 {:db/id               2
                       :house/_occupants    [[:db/id 0]]
                       :occupant/first-name "Kerry"}
                    3 {:db/id               3
                       :house/_occupants    [[:db/id 0]]
                       :occupant/first-name "John"}}}

           (-> {:house/address   "1 House Street"
                :house/occupants [{:occupant/first-name "Sam"}
                                  {:occupant/first-name "Kerry"}
                                  {:occupant/first-name "John"}]}
               (->pyramid-db)))))

  (testing "A query with derived data"
    (is (= {:db/id {0 {:db/id           0
                       :house/address   "1 House Street"
                       :house/occupants [[:db/id 1] [:db/id 2] [:db/id 3]]}
                    1 {:db/id               1
                       :house/_occupants    [[:db/id 0]]
                       :occupant/first-name "Sam"}
                    2 {:db/id               2
                       :house/_occupants    [[:db/id 0]]
                       :occupant/first-name "Kerry"}
                    3 {:db/id               3
                       :house/_occupants    [[:db/id 0]]
                       :occupant/first-name "John"}}}

           (-> {:house/address   "1 House Street"
                :house/occupants [{:occupant/first-name "Sam"}
                                  {:occupant/first-name "Kerry"}
                                  {:occupant/first-name "John"}]}
               (->pyramid-db))))))
