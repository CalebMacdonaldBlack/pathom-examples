(ns com.calebmacdonaldblack.cyclone.example.ordering
  (:require
    [com.calebmacdonaldblack.cyclone.pathom.plugin :as cyclone.pathom.plugin]
    [com.calebmacdonaldblack.cyclone.pathom.resolver :as cyclone.pathom.resolver]
    [com.wsscode.pathom.viz.ws-connector.core :as pvc]
    [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.plugin :as p.plugin]))

(def db-schema
  {:com.example/id                     {:db/unique :db.unique/identity}
   :com.example.order.price/discount   {}
   :com.example.product.price/discount {}
   :com.example.order/products         {:db/valueType   :db.type/ref
                                        :db/cardinality :db.cardinality/many}})

(pco/defresolver product-price-sub-total
  [_env input]
  {::pco/input  [:com.example.product/price
                 :com.example.product/quantity]
   ::pco/output [:com.example.product.price/sub-total]}
  (let [{:keys [com.example.product/price
                com.example.product/quantity]} input]
    {:com.example.product.price/sub-total
     (* price quantity)}))

(pco/defresolver order-sub-total-by-aggregating-product-sub-totals
  [_env input]
  {::pco/input  [{:com.example.order/products [:com.example.product.price/sub-total]}]
   ::pco/output [:com.example.order.price/sub-total]}
  (->> input
       :com.example.order/products
       (transduce (map :com.example.product.price/sub-total) +)
       (hash-map :com.example.order.price/sub-total)))

(pco/defresolver order-total-by-aggregating-product-totals
  [_env input]
  {::pco/input  [{:com.example.order/products [:com.example.product.price/total]}]
   ::pco/output [:com.example.order.price/total]}
  (->> input
       :com.example.order/products
       (transduce (map :com.example.product.price/total) +)
       (hash-map :com.example.order.price/total)))

(pco/defresolver product-total-from-sub-total-and-discount
  [_env input]
  {::pco/input  [:com.example.product.price/sub-total
                 (pco/? :com.example.product.price/discount)]
   ::pco/output [:com.example.product.price/total]}
  (let [{:keys [com.example.product.price/sub-total
                com.example.product.price/discount]
         :or   {discount 0.0}} input]
    {:com.example.product.price/total
     (* sub-total (- 1 discount))}))

(pco/defresolver product-discount-is-inherited-from-the-order
  [_env input]
  {::pco/input  [{:com.example/order [:com.example.order.price/discount]}]
   ::pco/output [:com.example.product.price/discount]}
  (let [{{:com.example.order.price/keys [discount]} :com.example/order} input]
    {:com.example.product.price/discount discount}))

(pco/defresolver reverse-lookup-for-products-is-an-order
  [_env input]
  {::pco/input  [{:com.example.order/_products [:com.example/id]}]
   ::pco/output [{:com.example/order [:com.example/id]}]}
  (let [{[{:com.example/keys [id]}] :com.example.order/_products} input]
    {:com.example/order {:com.example/id id}}))

(defn create-env []
  (-> (pci/register [product-price-sub-total
                     order-sub-total-by-aggregating-product-sub-totals
                     order-total-by-aggregating-product-totals
                     product-total-from-sub-total-and-discount
                     product-discount-is-inherited-from-the-order
                     reverse-lookup-for-products-is-an-order
                     (cyclone.pathom.resolver/schema->resolvers db-schema)])
      (assoc :com.example.db/schema db-schema)
      (p.plugin/register cyclone.pathom.plugin/transact-entity)
      (p.connector/connect-env {::pvc/parser-id `create-env})))
