(ns com.calebmacdonaldblack.cyclone.example3
  (:import (clojure.lang IDeref IRecord)))

(defrecord Foo []
  IDeref
  (deref [_]
    :foo!))

(->Foo)
; Error printing return value (IllegalArgumentException) at clojure.lang.MultiFn/findAndCacheBestMethod (MultiFn.java:179).
; Multiple methods in multimethod 'print-method' match dispatch value: class com.calebmacdonaldblack.cyclone.example3.Foo -> interface clojure.lang.IDeref and interface clojure.lang.IRecord, and neither is preferred

(prefer-method print-method IRecord IDeref)
; => #object[clojure.lang.MultiFn 0x2ab93614 "clojure.lang.MultiFn@2ab93614"]
