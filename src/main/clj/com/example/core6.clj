(ns com.example.core6
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

;; Nested data structure
(def house-data
  {:house/address   "1 House Street"
   :house/occupants [{:occupant/first-name "Sam"}
                     {:occupant/first-name "Kerry"}
                     {:occupant/first-name "John"}]})


;; Doesn't feel like it would scale with more attributes, materialised or otherwise.
(pco/defresolver occupants-with-address
  [{:house/keys [occupants address]}]
  {::pco/output [{:house/occupants-with-house [:house/address :occupant/first-name]}]}
  {:house/occupants-with-house
   (mapv #(assoc % :house/address address) occupants)})

;; Expect an address
(pco/defresolver occupant-summary
  [{:occupant/keys [first-name] :house/keys [address]}]
  {:occupant/summary (str first-name " lives at " address)})

;; Multistep pathom query to build fetch the data we need
;; Not the greatest of solutions because it would be nice for this step to be a resolver itself
(deftest occupant-summary-test
  (is (= ["Sam lives at 1 House Street"
          "Kerry lives at 1 House Street"
          "John lives at 1 House Street"]
         (-> (pci/register [occupant-summary occupants-with-address])
             (psm/smart-map house-data)
             (:house/occupants-with-house)
             (->> (map :occupant/summary))))))

