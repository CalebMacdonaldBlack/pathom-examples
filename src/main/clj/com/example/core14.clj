(ns com.example.core14
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql])
  (:import (clojure.lang MapEntry)))

;; Mock a database
(def house-database
  {:house/address   "1 House Street"
   :house/occupants [{:occupant/first-name "Sam"}
                     {:occupant/first-name "Kerry"}
                     {:occupant/first-name "John"}]})


;; Aggregate parent and child attributes into single child attribute
(pco/defresolver occupant-summary
  [{first-name :occupant/first-name address :house/address}]
  {::pco/input [:occupant/first-name :house/address]}
  {:occupant/summary (str first-name " lives at " address)})

(defn- flatten-map
  ([form] (flatten-map form form))
  ([form parent]
   (cond

     (and (map-entry? form) (coll? form))
     (let [[k v] form]
       (MapEntry. k (flatten-map v parent)))

     (map? form)
     (cond->> (into {} (map #(flatten-map % parent)) form)
       parent (merge parent))

     (coll? form)
     (mapv #(flatten-map % parent) form)

     :else
     form)))


(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street"}
                            {:occupant/summary "Kerry lives at 1 House Street"}
                            {:occupant/summary "John lives at 1 House Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process (flatten-map house-database)
                            [{:house/occupants [:occupant/summary]}]))))

  ;; However, the persistent database means we can't override inputs so this test fails
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 2 New Street"}
                            {:occupant/summary "Kerry lives at 2 New Street"}
                            {:occupant/summary "John lives at 2 New Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process (-> house-database
                                (assoc :house/address "2 New Street")
                                flatten-map)
                            [{:house/occupants [:occupant/summary]}])))))

(deftest flatten-map-test
  (is (= {:house/address   "1 House Street"
          :house/occupants [{:house/address       "1 House Street"
                             :house/occupants     [{:occupant/first-name "Sam"}
                                                   {:occupant/first-name "Kerry"}
                                                   {:occupant/first-name "John"}]
                             :occupant/first-name "Sam"}
                            {:house/address       "1 House Street"
                             :house/occupants     [{:occupant/first-name "Sam"}
                                                   {:occupant/first-name "Kerry"}
                                                   {:occupant/first-name "John"}]
                             :occupant/first-name "Kerry"}
                            {:house/address       "1 House Street"
                             :house/occupants     [{:occupant/first-name "Sam"}
                                                   {:occupant/first-name "Kerry"}
                                                   {:occupant/first-name "John"}]
                             :occupant/first-name "John"}]}
         (flatten-map house-database))))
