(ns com.calebmacdonaldblack.cyclone.example2
  (:require
    [clojure.set :as set]
    [clojure.test :refer :all]
    [com.wsscode.pathom.viz.ws-connector.core :as pvc]
    [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [com.wsscode.pathom3.plugin :as p.plugin]
    [datascript.core :as d]))

(defn datomic-simple-pull-attr-resolver
  [source target]
  (let [target-keyword (if (map? target) (ffirst target) target)]
    (pco/resolver
      (pbir/attr-alias-resolver-name source target-keyword "datomic-simple-pull-attr-resolver")
      (merge
        {::pco/input    [source]
         ::pco/output   [target]
         ::pco/priority 1})
      (fn [{:com.example/keys [db]} input]
        (update (d/pull db [target] (find input source))
          target #(or % ::pco/unknown-value))))))

(def split-qualified-keyword
  (juxt namespace name))

(defn- ->reverse-lookup-qualified-keyword [-keyword]
  (let [[-namespace -name] (split-qualified-keyword -keyword)]
    (keyword -namespace (str "_" -name))))

(defn datomic-reverse-lookup-resolver [identity-attr1 identity-attr2 source]
  (let [target (->reverse-lookup-qualified-keyword source)]
    (pco/resolver
      (pbir/attr-alias-resolver-name source target "datomic-reverse-lookup-one-result-resolver")
      {::pco/input  [identity-attr1]
       ::pco/output [{target [identity-attr2]}]}
      (fn [{:com.example/keys [db]} input]
        {target (-> (some-> (d/pull db [{target [identity-attr2]}] (find input identity-attr1))
                            (get target)
                            (seq)
                            vec)
                    (or ::pco/unknown-value))}))))

(defn schema->resolvers
  [schema]
  (let [identity?           (comp #{:db.unique/identity} :db/unique val)
        identity-attr-xform (comp (filter identity?) (map key))
        identity-attrs      (into [] identity-attr-xform schema)
        ref?                (comp #{:db.type/ref} :db/valueType val)
        one?                (comp #{:db.cardinality/one} :db/cardinality val)
        many?               (comp #{:db.cardinality/many} :db/cardinality val)
        value-attr-xform    (comp (remove ref?) (map key))
        value-attrs         (into [] value-attr-xform schema)
        ref-attr-xform      (comp (filter ref?) (map key))
        ref-attrs           (into [] ref-attr-xform schema)]
    (concat
      (for [value-attr    value-attrs
            identity-attr identity-attrs]
        (datomic-simple-pull-attr-resolver identity-attr value-attr))
      (for [ref-attr       ref-attrs
            identity-attr1 identity-attrs
            identity-attr2 identity-attrs]
        (datomic-simple-pull-attr-resolver identity-attr1 {ref-attr [identity-attr2]}))
      (for [ref-attr       ref-attrs
            identity-attr1 identity-attrs
            identity-attr2 identity-attrs]
        (datomic-reverse-lookup-resolver identity-attr1 identity-attr2 ref-attr)))))


(p.plugin/defplugin transact-entity
  {::pcr/wrap-merge-attribute
   (fn [original]
     (fn [{:com.example/keys [db] :as env} {:com.example/keys [id] :as out} k v]
       ;(d/transact! conn [{:com.example/id id k v}])
       (let [env (update env :com.example/db
                   d/db-with [{:com.example/id id k v}])]
         (original env out k v))))
   ::pcr/wrap-root-run-graph!
   (fn [original]
     (fn [{:com.example/keys [db] :as env} ast-or-graph entity-tree*]
       ;(d/transact! conn [@entity-tree*])
       (let [env (update env :com.example/db
                   d/db-with [@entity-tree*])]
         (original env ast-or-graph entity-tree*))))})


;; =====================================================================================================================


(def db-schema
  {:com.example/id                {:db/unique :db.unique/identity}
   :com.example.person/given-name {}
   :com.example.person/surname    {}
   :com.example.person/age        {}
   :com.example.person/children   {:db/valueType   :db.type/ref
                                   :db/cardinality :db.cardinality/many}
   :com.example.person/spouse     {:db/valueType   :db.type/ref
                                   :db/cardinality :db.cardinality/one}})

(def new-db
  (d/empty-db db-schema))

(pco/defresolver reverse-of-spouse-ref-is-spouse
  [_env input]
  {::pco/input  [{:com.example.person/_spouse [:com.example/id]}]
   ::pco/output [{:com.example.person/spouse [:com.example/id]}]}
  (-> input
      (set/rename-keys {:com.example.person/_spouse :com.example.person/spouse})
      (update :com.example.person/spouse first)))

(pco/defresolver reverse-of-children-ref-is-parents
  [_env input]
  {::pco/input  [{:com.example.person/_children [:com.example/id]}]
   ::pco/output [{:com.example.person/parents [:com.example/id]}]}
  (set/rename-keys input {:com.example.person/_children :com.example.person/parents}))

(pco/defresolver inherit-person-surname-from-spouse
  [_env input]
  {::pco/input  [{:com.example.person/spouse [:com.example.person/surname]}]
   ::pco/output [:com.example.person/surname]}
  {:com.example.person/surname (get-in input [:com.example.person/spouse :com.example.person/surname] ::pco/unknown-value)})

(pco/defresolver inherit-person-children-from-spouse
  [_env input]
  {::pco/input  [{:com.example.person/spouse [{:com.example.person/children [:com.example/id]}]}]
   ::pco/output [{:com.example.person/children [:com.example/id]}]}
  {:com.example.person/children (get-in input [:com.example.person/spouse :com.example.person/children] ::pco/unknown-value)})

(pco/defresolver inherit-surname-from-parent
  [_env input]
  {::pco/input  [{:com.example.person/parents [:com.example.person/surname]}]
   ::pco/output [:com.example.person/surname]}
  {:com.example.person/surname (get-in input [:com.example.person/parents 0 :com.example.person/surname] ::pco/unknown-value)})

(pco/defresolver person-full-name
  [_env {:com.example.person/keys [given-name surname]}]
  {:com.example.person/full-name
   (str given-name " " surname)})

(pco/defresolver person-babies
  [_env input]
  {::pco/input  [{:com.example.person/children [:com.example.person/age :com.example/id]}]
   ::pco/output [{:com.example.person/babies [:com.example/id]}]}
  (let [baby? (comp zero? :com.example.person/age)]
    {:com.example.person/babies
     (into []
       (filter baby?)
       (:com.example.person/children input))}))


(pco/defresolver default-person-spouse
  [_ _]
  {::pco/output   [{:com.example.person/spouse [:com.example/id
                                                :com.example.person/age
                                                :com.example.person/given-name]}]
   ::pco/priority -1}
  {:com.example.person/spouse
   {:com.example/id                "lisa"
    :com.example.person/age        25
    :com.example.person/given-name "Lisa Rose"}})

;; Interrogate entity-tree* and resolvers and input to infer structure without needing the db-schema
;; Can id be optional for nested data?
(def env
  (-> (pci/register [
                     default-person-spouse
                     inherit-person-surname-from-spouse
                     inherit-surname-from-parent
                     inherit-person-children-from-spouse
                     reverse-of-spouse-ref-is-spouse
                     reverse-of-children-ref-is-parents
                     person-full-name
                     person-babies
                     (schema->resolvers db-schema)])

      (p.plugin/register [transact-entity])
      (assoc :com.example/db new-db)
      (p.connector/connect-env {::pvc/parser-id `env})))

(deftest foo-test

  #_(testing "What is the full name of John's spouse?"
      (is (= {:com.example.person/spouse {:com.example.person/full-name "Lisa Rose Macdonald Black"}}
             (p.eql/process
               env
               {:com.example/id                "john"
                :com.example.person/age        26
                :com.example.person/given-name "John Ian Geoffrey"
                :com.example.person/surname    "Macdonald Black"
                :com.example.person/children   {:com.example/id                "henry"
                                                :com.example.person/given-name "Henry Micheal"
                                                :com.example.person/age        7}}
               [:com.example.person/given-name
                {:com.example.person/spouse
                 [:com.example.person/surname
                  :com.example.person/children]}
                {:com.example.person/children
                 [:com.example.person/surname]}]))))


  (is (= {:com.example.person/age        26
          :com.example.person/babies     [{:com.example.person/age        0
                                           :com.example.person/full-name  "Allison Macdonald Black"
                                           :com.example.person/given-name "Allison"
                                           :com.example.person/parents    [{:com.example.person/full-name "John Ian Geoffrey Macdonald Black"}]
                                           :com.example.person/surname    "Macdonald Black"}]
          :com.example.person/children   [{:com.example.person/age        7
                                           :com.example.person/full-name  "Henry Micheal Macdonald Black"
                                           :com.example.person/given-name "Henry Micheal"
                                           :com.example.person/parents    [{:com.example.person/full-name "John Ian Geoffrey Macdonald Black"}]
                                           :com.example.person/surname    "Macdonald Black"}
                                          {:com.example.person/age        5
                                           :com.example.person/full-name  "Alex Geoffrey Macdonald Black"
                                           :com.example.person/given-name "Alex Geoffrey"
                                           :com.example.person/parents    [{:com.example.person/full-name "John Ian Geoffrey Macdonald Black"}]
                                           :com.example.person/surname    "Macdonald Black"}
                                          {:com.example.person/age        0
                                           :com.example.person/full-name  "Allison Macdonald Black"
                                           :com.example.person/given-name "Allison"
                                           :com.example.person/parents    [{:com.example.person/full-name "John Ian Geoffrey Macdonald Black"}]
                                           :com.example.person/surname    "Macdonald Black"}]
          :com.example.person/given-name "John Ian Geoffrey"
          :com.example.person/spouse     {:com.example.person/_spouse    [{:com.example/id "john"}]
                                          :com.example.person/age        25
                                          :com.example.person/children   [{:com.example/id "henry"}
                                                                          {:com.example/id "alex"}
                                                                          {:com.example/id "ally"}]
                                          :com.example.person/given-name "Lisa Rose"
                                          :com.example.person/spouse     {:com.example.person/children  [{:com.example/id "henry"}
                                                                                                         {:com.example/id "alex"}
                                                                                                         {:com.example/id "ally"}]
                                                                          :com.example.person/full-name "John Ian Geoffrey Macdonald Black"}
                                          :com.example.person/surname    "Macdonald Black"}
          :com.example.person/surname    "Macdonald Black"
          :com.example/id                "john"}

         (p.eql/process
           env
           {:com.example/id                "john"
            :com.example.person/age        26
            :com.example.person/given-name "John Ian Geoffrey"
            :com.example.person/surname    "Macdonald Black"
            ;:com.example.person/spouse     {:com.example/id                "lisa"
            ;                                :com.example.person/age        25
            ;                                :com.example.person/given-name "Lisa Rose"}
            :com.example.person/children   [{:com.example/id                "henry"
                                             :com.example.person/given-name "Henry Micheal"
                                             :com.example.person/age        7}
                                            {:com.example/id                "alex"
                                             :com.example.person/given-name "Alex Geoffrey"
                                             :com.example.person/age        5}
                                            {:com.example/id                "ally"
                                             :com.example.person/given-name "Allison"
                                             :com.example.person/age        0}]}
           '[:com.example/id
             :com.example.person/given-name
             :com.example.person/age
             :com.example.person/surname
             {:com.example.person/spouse [:com.example.person/given-name
                                          :com.example.person/age
                                          :com.example.person/surname
                                          :com.example.person/children
                                          :com.example.person/_spouse
                                          {:com.example.person/spouse [:com.example.person/full-name
                                                                       :com.example.person/children]}]}
             {:com.example.person/babies [:com.example.person/given-name
                                          :com.example.person/age
                                          :com.example.person/surname
                                          :com.example.person/full-name
                                          {:com.example.person/parents [:com.example.person/full-name]}]}
             {:com.example.person/children [:com.example.person/given-name
                                            :com.example.person/age
                                            :com.example.person/surname
                                            :com.example.person/full-name
                                            {:com.example.person/parents [:com.example.person/full-name]}]}]))))
