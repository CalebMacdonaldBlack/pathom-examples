(ns com.example.core20
  (:require
    [clojure.test :refer :all]
    [com.wsscode.misc.coll :as coll]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.format.eql :as pf.eql]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [datascript.core :as d]))

(def person-first-name-lookup
  {"john" {:com.example.person/first-name "John"}
   "mary" {:com.example.person/first-name "Mary"}})

(def family-surname-lookup
  {"smith" {:com.example.family/surname "Smith"}})

(def family-members-lookup
  {"smith" {:com.example.family/members [{:com.example/id "john"}
                                         {:com.example/id "mary"}]}})

(def family-surname-by-id
  (pbir/attribute-table-resolver
    :com.example.family.surname/lookup
    :com.example/id
    [:com.example.family/surname]))

(def family-members-by-id
  (pbir/attribute-table-resolver
    :com.example.family.members/lookup
    :com.example/id
    [{:com.example.family/members [:com.example/id]}]))

(pco/defresolver family-members-by-id
  [input]
  {::pco/input  [:com.example/id
                 {:com.example.family/members [:com.example/id]}]
   ::pco/output [:com.example.family.members/reverse-lookup]}
  {:com.example.family.members/reverse-lookup})

(def person-first-name-by-id
  (pbir/attribute-table-resolver
    :com.example.person.first-name/lookup
    :com.example/id
    [:com.example.person/first-name]))

(def indexes
  (pci/register
    [family-surname-by-id
     family-members-by-id
     person-first-name-by-id]))

(deftest family-surname-by-id-test
  (is (= {:com.example.family/surname "Smith"}
         (p.eql/process
           indexes
           {:com.example.family.surname/lookup family-surname-lookup
            :com.example.family.members/lookup family-members-lookup
            :com.example/id                    "smith"}
           [:com.example.family/surname
            {:com.example.family/members
             [:com.example.person/first-name]}]))))
