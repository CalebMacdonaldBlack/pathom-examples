(ns com.example.core3
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

;; Nested data structure
(def house-data
  {:house/address   "1 House Street"
   :house/occupants [{:occupant/first-name "Sam"}
                     {:occupant/first-name "Kerry"}
                     {:occupant/first-name "John"}]})

;; Imaginary Datomic style reverse lookup
(pco/defresolver occupant-summary [{:occupant/keys [first-name] :house/keys [_occupants]}]
  {::pco/input [:occupant/first-name {:house/_occupants [:house/address]}]}
  {:occupant/summary (str first-name " lives at " :house/address _occupants)})

;; Failing test because, duh.
(deftest occupant-summary-test
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street"}
                            {:occupant/summary "Kerry lives at 1 House Street"}
                            {:occupant/summary "John lives at 1 House Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process house-data
               [{:house/occupants [:occupant/summary]}])))))
