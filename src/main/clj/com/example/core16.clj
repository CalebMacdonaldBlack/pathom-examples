(ns com.example.core16
  (:require
    [clojure.test :refer :all]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

(def family-db
  (pbir/constantly-resolver
    :com.example.family/db
    {"Family1" {:com.example.family/surname "Smith"
                :com.example.family/members [{:com.example.person/first-name "John"}
                                             {:com.example.person/first-name "Mary"}]}}))

(def family-by-id
  (pbir/attribute-table-resolver
    :com.example.family/db
    :com.example.family/id
    [:com.example.family/surname
     {:com.example.family/members [:com.example.person/first-name]}]))

(pco/defresolver family-member-surnames
  [_env {:com.example.family/keys [members surname]}]
  {
   ::pco/input  [:com.example.family/surname {:com.example.family/members [:com.example.person/first-name]}]
   ::pco/output [{:com.example.family/members [:com.example.person/surname]}]}
  {:com.example.family/members
   (mapv (constantly {:com.example.person/surname surname}) members)})

(def env
  (pci/register
    [family-db
     family-by-id
     family-member-surnames]))

(deftest derive-person-surname-from-family-test
  ;; Fails
  (is (= {:com.example.family/members
          [{:com.example.person/first-name "John"
            :com.example.person/surname    "Smith"}
           {:com.example.person/first-name "Mary"
            :com.example.person/surname    "Smith"}]}
         (p.eql/process
           env
           {:com.example.family/id "Family1"}
           [{:com.example.family/members
             [:com.example.person/first-name
              :com.example.person/surname]}]))))
