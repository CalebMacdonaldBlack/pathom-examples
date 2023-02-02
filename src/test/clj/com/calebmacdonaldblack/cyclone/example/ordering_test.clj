(ns com.calebmacdonaldblack.cyclone.example.ordering-test
  (:require
    [clojure.test :refer :all]
    [com.calebmacdonaldblack.cyclone.example.ordering :as ordering]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

(deftest product-sub-total-test
  (testing "Should derive sub-total from price and quantity"
    (let [env (ordering/create-env)]
      (is (= {:com.example.product.price/sub-total 20.0}
             (p.eql/process
               env
               {:com.example/id               "green-apple"
                :com.example.product/title    "Green Apple"
                :com.example.product/quantity 4
                :com.example.product/price    5.0}
               [:com.example.product.price/sub-total]))))))

(deftest order-sub-total-test
  (testing "Should derive sub-total by aggregating sub-totals of the products in the order"
    (let [env (ordering/create-env)]
      (is (= {:com.example.order.price/sub-total 23.0}
             (p.eql/process
               env
               {:com.example/id "order-1"
                :com.example.order/products
                [{:com.example/id               "green-apple"
                  :com.example.product/title    "Green Apple"
                  :com.example.product/quantity 4
                  :com.example.product/price    5.0}
                 {:com.example/id               "red-apple"
                  :com.example.product/title    "Red Apple"
                  :com.example.product/quantity 1
                  :com.example.product/price    3.0}]}
               [:com.example.order.price/sub-total]))))))

(deftest product-sub-total-with-discount-test
  (testing "Product total price has discount applied"
    (let [env (ordering/create-env)]
      (is (= {:com.example.product.price/total 17.0}
             (p.eql/process
               env
               {:com.example/id                     "green-apple"
                :com.example.product/title          "Green Apple"
                :com.example.product/quantity       4
                :com.example.product/price          5.0
                :com.example.product.price/discount 0.15}
               [:com.example.product.price/total])))))

  (testing "Product total price when no discount available"
    (let [env (ordering/create-env)]
      (is (= {:com.example.product.price/total 20.0}
             (p.eql/process
               env
               {:com.example/id               "green-apple"
                :com.example.product/title    "Green Apple"
                :com.example.product/quantity 4
                :com.example.product/price    5.0}
               [:com.example.product.price/total]))))))

(deftest product-with-order-test
  (testing "Product has a relationship with the order it belongs to"
    (let [env (ordering/create-env)]
      (is (= {:com.example.order/products
              [{:com.example.product/title "Green Apple"
                :com.example/order
                {:com.example.order.price/discount 0.1
                 :com.example/id                   "order-1"}}]}
             (p.eql/process
               env
               {:com.example/id                   "order-1"
                :com.example.order.price/discount 0.1
                :com.example.order/products
                [{:com.example/id               "green-apple"
                  :com.example.product/title    "Green Apple"
                  :com.example.product/quantity 4
                  :com.example.product/price    5.0}]}
               [{:com.example.order/products
                 [:com.example.product/title
                  {:com.example/order
                   [:com.example/id
                    :com.example.order.price/discount]}]}]))))))

(deftest product-with-discount-inherited-from-the-order-test
  (testing "Product discount is inherited from its order"
    (let [env (ordering/create-env)]
      (is (= {:com.example.order/products
              [{:com.example.product.price/discount 0.1}]}
             (p.eql/process
               env
               {:com.example/id                   "order-1"
                :com.example.order.price/discount 0.1
                :com.example.order/products
                [{:com.example/id               "green-apple"
                  :com.example.product/title    "Green Apple"
                  :com.example.product/quantity 4
                  :com.example.product/price    5.0}]}
               [{:com.example.order/products
                 [:com.example.product.price/discount]}]))))))

(deftest product-totals-with-discount-inherited-from-the-order-test
  (testing "Product total price calculated with the inherited discount from the order"
    (let [env (ordering/create-env)]
      (is (= {:com.example.order/products
              [{:com.example.product.price/total 15.0}
               {:com.example.product.price/total 3.6}]}
             (p.eql/process
               env
               {:com.example/id                   "order-1"
                :com.example.order.price/discount 0.1
                :com.example.order/products
                [{:com.example/id                     "green-apple"
                  :com.example.product/title          "Green Apple"
                  :com.example.product.price/discount 0.25
                  :com.example.product/quantity       4
                  :com.example.product/price          5.0}
                 {:com.example/id               "red-apple"
                  :com.example.product/title    "Red Apple"
                  :com.example.product/quantity 2
                  :com.example.product/price    2.0}]}
               [{:com.example.order/products
                 [:com.example.product.price/total]}]))))))

(deftest order-total-with-discounts-applied-test
  (testing "Order total calculated using discounts that may or may not be inherited"
    (let [env (ordering/create-env)]
      (is (= {:com.example.order.price/total 18.6}
             (p.eql/process
               env
               {:com.example/id                   "order-1"
                :com.example.order.price/discount 0.1
                :com.example.order/products
                [{:com.example/id                     "green-apple"
                  :com.example.product/title          "Green Apple"
                  :com.example.product.price/discount 0.25
                  :com.example.product/quantity       4
                  :com.example.product/price          5.0}
                 {:com.example/id               "red-apple"
                  :com.example.product/title    "Red Apple"
                  :com.example.product/quantity 2
                  :com.example.product/price    2.0}]}
               [:com.example.order.price/total]))))))
