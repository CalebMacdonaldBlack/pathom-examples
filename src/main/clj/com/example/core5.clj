(ns com.example.core5
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

;; Expect an address
(pco/defresolver occupant-summary
  [{:occupant/keys [first-name] :house/keys [address]}]
  {:occupant/summary (str first-name " lives at " address)})

;; Multistep pathom query to build fetch the data we need
;; Not the greatest of solutions because it would be nice for this step to be a resolver itself
(deftest occupant-summary-test
  (is (= {:house/address   "1 House Street"
          :house/occupants ["Sam lives at 1 House Street"
                            "Kerry lives at 1 House Street"
                            "John lives at 1 House Street"]}
         (let [house (-> (pci/register [occupant-summary])
                         (psm/smart-map house-data))]
           (update house :house/occupants
             #(->> %
                   (mapv (partial merge house))
                   (mapv :occupant/summary)))))))

