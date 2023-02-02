(ns com.example.core21
  (:require
    [clojure.test :refer :all]
    [com.wsscode.misc.coll :as coll]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.format.eql :as pf.eql]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [datascript.core :as d]))

(def family-entity-data
  {:com.example.family/surname                   "smith"
   :com.example.family.members/lookup            {"john" {}
                                                  "mary" {}}
   :com.example.family.members.first-name/lookup {"john" {:com.example.person/first-name "John"}
                                                  "mary" {:com.example.person/first-name "Mary"}}})

(pco/defresolver
  family-member-surname-lookup
  [{family-members-lookup :com.example.family.members/lookup
    family-surname        :com.example.family/surname}]
  {:com.example.family.members.surname/lookup
   (update-vals family-members-lookup
     #(assoc % :com.example.person/surname family-surname))})

(pco/defresolver
  family-member-full-name-lookup
  [{family-members-lookup     :com.example.family.members/lookup
    family-member-first-names :com.example.family.members.first-name/lookup
    family-member-surnames    :com.example.family.members.surname/lookup}]
  {:com.example.family.members.full-name/lookup
   (into {}
     (map (fn [[id _]]
            (let [first-name (get-in family-member-first-names [id :com.example.person/first-name])
                  surname    (get-in family-member-surnames [id :com.example.person/surname])]
              (when (and first-name surname)
                [id {:com.example.person/full-name (str first-name " " surname)}]))))
     family-members-lookup)})

(def indexes
  (pci/register
    [family-member-surname-lookup
     family-member-full-name-lookup]))

(deftest family-surname-by-id-test
  (is (= {}
         (p.eql/process
           indexes
           family-entity-data
           [:com.example.family/surname
            :com.example.family.members.full-name/lookup]))))
