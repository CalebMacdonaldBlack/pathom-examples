(ns com.example.core
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

;; Requires parent 'address' attribute and child 'first-name' attribute
(pco/defresolver occupant-summary [{:house/keys [address occupants]}]
  {::pco/input  [:house/address {:house/occupants [:occupant/first-name]}]
   ::pco/output [{:house/occupants [:occupant/summary]}]}
  {:house/occupants
   (for [{:occupant/keys [first-name]} occupants]
     {:occupant/summary (str first-name " lives at " address)})})

;; Failing test.
;; The 'occupant-summary' resolver is ignored entirely because :house/occupants exists in the input
(deftest occupant-summary-test
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 1 House Street"}
                            {:occupant/summary "Kerry lives at 1 House Street"}
                            {:occupant/summary "John lives at 1 House Street"}]}
         (-> (pci/register [occupant-summary])
             (p.eql/process house-data
               [{:house/occupants [:occupant/summary]}])))))
