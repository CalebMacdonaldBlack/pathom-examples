(ns com.example.core2
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

;; Mock a database
(def house-database
  {0 {:house/id      0
      :house/address "1 House Street"}
   1 {:occupant/id         1
      :house/id            0
      :occupant/first-name "Sam"}
   2 {:occupant/id         2
      :house/id            0
      :occupant/first-name "Kerry"}
   3 {:occupant/id         3
      :house/id            0
      :occupant/first-name "John"}})

;; Fetch house by id
(pco/defresolver house
  [{:house/keys [id]}]
  {::pco/output [:house/address {:house/occupants [:occupant/id]}]}
  (-> house-database
      (get id)
      (select-keys [:house/address])
      (assoc :house/occupants
        (->> house-database
             vals
             (filterv :occupant/id)
             (mapv #(select-keys % [:occupant/id]))))))

;; Fetch resolver by id
(pco/defresolver occupant
  [{:occupant/keys [id]}]
  {::pco/output [:occupant/first-name :house/id]}
  (-> house-database
      (get id)
      (select-keys [:occupant/first-name :house/id])))

;; Aggregate parent and child attributes into single child attribute
(pco/defresolver occupant-summary
  [{:house/keys [address] :occupant/keys [first-name]}]
  {:occupant/summary (str first-name " lives at " address)})

(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street"}
                            {:occupant/summary "Kerry lives at 1 House Street"}
                            {:occupant/summary "John lives at 1 House Street"}]}
         (-> (pci/register [occupant-summary house occupant])
             (p.eql/process {:house/id 0}
               [{:house/occupants [:occupant/summary]}]))))

  ;; However, the persistent database means we can't override inputs so this test fails
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 2 New Street"}
                            {:occupant/summary "Kerry lives at 2 New Street"}
                            {:occupant/summary "John lives at 2 New Street"}]}
         (-> (pci/register [occupant-summary house occupant])
             (p.eql/process {:house/id      0
                             :house/address "2 New Street"}
               [{:house/occupants [:occupant/summary]}])))))

;; Sanity check unit test
(deftest house-test
  (is (= {:house/address   "1 House Street"
          :house/occupants [{:occupant/id 1}
                            {:occupant/id 2}
                            {:occupant/id 3}]}
         (-> (pci/register [house])
             (p.eql/process {:house/id 0}
               [:house/address :house/occupants])))))

;; Sanity check unit test
(deftest occupant-test
  (is (= {:house/id            0
          :occupant/first-name "Sam"}
         (-> (pci/register [occupant])
             (p.eql/process {:occupant/id 1}
               [:occupant/first-name :house/id])))))
