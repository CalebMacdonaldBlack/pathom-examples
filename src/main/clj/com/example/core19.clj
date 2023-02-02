(ns com.example.core19
  (:require
    [clojure.test :refer :all]
    [com.wsscode.misc.coll :as coll]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.format.eql :as pf.eql]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [datascript.core :as d]))

(pco/defresolver family-members
  [_ {family :db/id :keys [conn]}]
  {::pco/output [{:com.example.family/members [:db/id :conn]}]}
  {:com.example.family/members
   (->> (d/pull @conn [:com.example.family/members] family)
        :com.example.family/members
        (mapv (fn [{:db/keys [id]}]
                {:db/id id
                 :conn  conn})))})

(pco/defresolver person-first-name
  [_ {person :db/id :keys [conn]}]
  {::pco/input  [:db/id :conn]
   ::pco/output [:com.example.person/first-name]}
  (d/pull @conn [:com.example.person/first-name] person))

(pco/defresolver person-full-name
  [_ input]
  {::pco/input  [:com.example.person/first-name
                 {:com.example.person/family [:com.example.family/surname]}]
   ::pco/output [:com.example.person/full-name]}
  (let [{:com.example.person/keys             [first-name]
         {:com.example.family/keys [surname]} :com.example.person/family} input]
    {:com.example.person/full-name
     (str first-name " " surname)}))

(pco/defresolver person-family
  [_ {person :db/id :keys [conn]}]
  {::pco/input  [:db/id :conn]
   ::pco/output [{:com.example.person/family [:db/id :conn]}]}
  {:com.example.person/family (-> (d/pull @conn [:com.example.family/_members] person)
                                  :com.example.family/_members
                                  first
                                  (assoc :conn conn))})

(pco/defresolver family-surname
  [_ {person :db/id :keys [conn]}]
  {::pco/input  [:db/id :conn]
   ::pco/output [:com.example.family/surname]}
  (d/pull @conn [:com.example.family/surname] person))

(def env
  (-> (pci/register [family-members person-first-name family-surname person-family person-full-name])
      (update ::pf.eql/map-select-include coll/sconj :db)))

(def entity
  (let [conn (d/create-conn {:com.example.family/members
                             {:db/cardinality :db.cardinality/many
                              :db/valueType   :db.type/ref}})
        {{:strs [smith-family]} :tempids}
        (d/transact! conn [{:db/id                      "smith-family"
                            :com.example.family/surname "Smith"
                            :com.example.family/members [{:com.example.person/first-name "John"}
                                                         {:com.example.person/first-name "Mary"}]}])]
    {:db/id smith-family
     :conn  conn}))

(p.eql/process
  env
  entity
  [:com.example.family/surname
   {:com.example.family/members [:com.example.person/first-name
                                 :com.example.person/full-name
                                 {:com.example.person/family
                                  [:com.example.family/surname]}]}])
