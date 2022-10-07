(ns com.example.core12
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql])
  (:import (clojure.lang MapEntry)))

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


;; Mock a database
(def house-database
  (let [house {:house/address   "1 House Street"
               :house/occupants [{:occupant/first-name "Sam"}
                                 {:occupant/first-name "Kerry"}
                                 {:occupant/first-name "John"}]}]
    (update house :house/occupants
      (fn [occupants]
        (mapv #(assoc % :occupant/house house) occupants)))))

;; Aggregate parent and child attributes into single child attribute
(pco/defresolver occupant-summary
  [{:occupant/keys [first-name] {:house/keys [address occupant-count]} :occupant/house}]
  {::pco/input [:occupant/first-name {:occupant/house [:house/address :house/occupant-count]}]}
  {:occupant/summary (str first-name " lives at " address " with " (dec occupant-count) " others.")})

;; Fetch number of occupants in house
(pco/defresolver occupant-count
  [{:house/keys [occupants]}]
  {::pco/output [:house/occupant-count]}
  {:house/occupant-count (count occupants)})


(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street with 2 others."}
                            {:occupant/summary "Kerry lives at 1 House Street with 2 others."}
                            {:occupant/summary "John lives at 1 House Street with 2 others."}]}
         (-> (pci/register [occupant-summary occupant-count])
             (p.eql/process house-database
                            [{:house/occupants [:occupant/summary]}])))))
