(ns com.example.core9
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [clojure.string :as str]))

;; Mock a database
(def house-database
  {0 {:db/id         0
      :house/address "1 House Street"}
   1 {:db/id               1
      :occupant/house      {:db/id 0}
      :occupant/first-name "Sam"}
   2 {:db/id               2
      :occupant/house      {:db/id 0}
      :occupant/first-name "Kerry"}
   3 {:db/id               3
      :occupant/house      {:db/id 0}
      :occupant/first-name "John"}})

;; Fetch house by id
(pco/defresolver house
  [{:db/keys [id]}]
  {::pco/output [:house/address {:house/occupants [:db/id]}]}
  (prn :house id)
  (prn
    (-> house-database
        (get id)
        (select-keys [:house/address])
        (assoc :house/occupants
          (->> house-database
               vals
               (filterv :occupant/first-name)
               (mapv #(select-keys % [:db/id]))))))
  (-> house-database
      (get id)
      (select-keys [:house/address])
      (assoc :house/occupants
        (->> house-database
             vals
             (filterv :occupant/first-name)
             (mapv #(select-keys % [:db/id]))))))

;; Fetch resolver by id
(pco/defresolver occupant
  [{:db/keys [id]}]
  {::pco/output [:occupant/first-name {:occupant/house [:db/id]}]}
  (prn :occupant id)
  (-> house-database
      (get id)
      (select-keys [:occupant/first-name :occupant/house])))

;; Fetch number of occupants in house
(pco/defresolver occupant-count
  [{:house/keys [occupants]}]
  {::pco/output [:house/occupant-count]}
  {:house/occupant-count (count occupants)})

;; Fetch number of occupants in house
(pco/defresolver house-occupant-summaries
  [{occupants :house/occupants}]
  {::pco/input [{:house/occupants [:occupant/summary]}]}
  {:house/occupant-summaries (str/join "\n" (mapv :occupant/summary occupants))})

;; Aggregate parent and child attributes into single child attribute
(pco/defresolver occupant-summary
  [{:occupant/keys [first-name] {:house/keys [address occupant-count]} :occupant/house}]
  {::pco/input [:occupant/first-name {:occupant/house [:house/address :house/occupant-count]}]}
  {:occupant/summary (str first-name " lives at " address " with " (dec occupant-count) " others.")})

(deftest occupant-summary-test

  ;; The graph can now find the address for an occupant using joins
  (is (= {:house/occupant-summaries "Sam lives at 1 House Street with 2 others.
Kerry lives at 1 House Street with 2 others.
John lives at 1 House Street with 2 others."
          :house/occupants          [{:occupant/summary "Sam lives at 1 House Street with 2 others."}
                                     {:occupant/summary "Kerry lives at 1 House Street with 2 others."}
                                     {:occupant/summary "John lives at 1 House Street with 2 others."}]}
         (-> (pci/register [occupant-summary house occupant occupant-count house-occupant-summaries])
             (p.eql/process {:db/id 0}
                            [:house/occupant-summaries
                             {:house/occupants [:occupant/summary]}]))))

  ;; However, the persistent database means we can't override inputs so this test fails
  (is (= {:house/occupants [{:occupant/summary "Sam lives at 2 New Street"}
                            {:occupant/summary "Kerry lives at 2 New Street"}
                            {:occupant/summary "John lives at 2 New Street"}]}
         (-> (pci/register [occupant-summary house occupant])
             (p.eql/process {:db/id         0
                             :house/address "2 New Street"}
                            [{:house/occupants [:occupant/summary]}])))))

;; Sanity check unit test
(deftest house-test
  (is (= {:house/address   "1 House Street"
          :house/occupants [{:db/id 1} {:db/id 2} {:db/id 3}]}
         (-> (pci/register [house])
             (p.eql/process {:db/id 0}
                            [:house/address :house/occupants])))))

;; Sanity check unit test
(deftest occupant-test
  (is (= {:occupant/first-name "Sam"
          :occupant/house      {:db/id 0}}
         (-> (pci/register [occupant])
             (p.eql/process {:db/id 1}
                            [:occupant/first-name :occupant/house])))))

;; Sanity check unit test
(deftest occupant-count-test
  (is (= {:house/occupant-count 3}
         (-> (pci/register [occupant-count])
             (p.eql/process {:house/occupants [{} {} {}]}
                            [:house/occupant-count])))))

;; Sanity check unit test
(deftest occupant-count-test
  (is (= {:house/occupant-summaries "Foo\nBar"}
         (-> (pci/register [house-occupant-summaries])
             (p.eql/process {:house/occupants [{:occupant/summary "Foo"} {:occupant/summary "Bar"}]}
                            [:house/occupant-summaries])))))
