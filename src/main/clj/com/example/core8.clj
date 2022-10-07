(ns com.example.core8
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

;; Mock a database
(def house-database
  (let [address {:house/address "1 House Street"}]
    (assoc address
      :house/occupants [{:occupant/first-name "Sam"
                         :occupant/house      address}
                        {:occupant/first-name "Kerry"
                         :occupant/house      address}
                        {:occupant/first-name "John"
                         :occupant/house      address}])))

;; Aggregate parent and child attributes into single child attribute
(pco/defresolver occupant-summary
  [{first-name :occupant/first-name {address :house/address} :occupant/house}]
  {::pco/input [:occupant/first-name {:occupant/house [:house/address]}]}
  {:occupant/summary (str first-name " lives at " address)})

(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street"}
                            {:occupant/summary "Kerry lives at 1 House Street"}
                            {:occupant/summary "John lives at 1 House Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process house-database
                            [{:house/occupants [:occupant/summary]}]))))

  ;; However, the persistent database means we can't override inputs so this test fails
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 2 New Street"}
                            {:occupant/summary "Kerry lives at 2 New Street"}
                            {:occupant/summary "John lives at 2 New Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process {:house/address "2 New Street"}
                            [{:house/occupants [:occupant/summary]}])))))
