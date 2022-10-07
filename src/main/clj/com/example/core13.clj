(ns com.example.core13
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
  [{first-name :occupant/first-name house :house/_occupants}]
  {::pco/input [:occupant/first-name {:house/_occupants [:house/address]}]}
  {:occupant/summary (str first-name " lives at " (-> house first :house/address))})

(defn- add-reverse-lookups
  ([form] (add-reverse-lookups form nil))
  ([form [parent-k parent-v :as parent]]
   (cond

     (and (map-entry? form) (coll? form))
     (let [[k v] form]
       (MapEntry. k (add-reverse-lookups v [(keyword (namespace k) (str "_" (name k))) [parent-v]])))

     (map? form)
     (cond->> (into {} (map #(add-reverse-lookups % [nil form])) form)
       parent (merge {parent-k parent-v}))

     (coll? form)
     (mapv #(add-reverse-lookups % parent) form)

     :else
     form)))


(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street"}
                            {:occupant/summary "Kerry lives at 1 House Street"}
                            {:occupant/summary "John lives at 1 House Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process (add-reverse-lookups house-database)
                            [{:house/occupants [:occupant/summary]}]))))

  ;; However, the persistent database means we can't override inputs so this test fails
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 2 New Street"}
                            {:occupant/summary "Kerry lives at 2 New Street"}
                            {:occupant/summary "John lives at 2 New Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process (-> house-database
                                (assoc :house/address "2 New Street")
                                add-reverse-lookups)
                            [{:house/occupants [:occupant/summary]}])))))

(deftest add-reverse-lookups-test
  (is (= {:house/address   "1 House Street"
          :house/occupants [{:house/_occupants    [{:house/address   "1 House Street"
                                                    :house/occupants [{:occupant/first-name "Sam"}
                                                                      {:occupant/first-name "Kerry"}
                                                                      {:occupant/first-name "John"}]}]
                             :occupant/first-name "Sam"}
                            {:house/_occupants    [{:house/address   "1 House Street"
                                                    :house/occupants [{:occupant/first-name "Sam"}
                                                                      {:occupant/first-name "Kerry"}
                                                                      {:occupant/first-name "John"}]}]
                             :occupant/first-name "Kerry"}
                            {:house/_occupants    [{:house/address   "1 House Street"
                                                    :house/occupants [{:occupant/first-name "Sam"}
                                                                      {:occupant/first-name "Kerry"}
                                                                      {:occupant/first-name "John"}]}]
                             :occupant/first-name "John"}]}
         (add-reverse-lookups house-database))))
