(ns com.calebmacdonaldblack.cyclone.pathom.resolver
  (:require
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.operation :as pco]
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

