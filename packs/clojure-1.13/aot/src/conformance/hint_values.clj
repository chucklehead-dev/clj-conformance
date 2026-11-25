(ns conformance.hint-values)

(defmacro pass [expr] expr)

(defn run []
  (let [v ^{:fixture/origin :literal} [:payload]
        events (atom [])
        mark (fn [k] (swap! events conj k) v)
        ordinary ^{:tag clojure.lang.IPersistentVector :fixture/origin :syntax}
                 (pass (mark :ordinary))
        tail ((fn [] ^clojure.lang.IPersistentVector (pass (mark :tail))))
        looped (loop [i 2]
                 (swap! events conj i)
                 (if (pos? i) (recur (dec i)) (pass (mark :done))))]
    {:events @events
     :observations (mapv (fn [x] [x (meta x) (identical? v x)])
                         [ordinary tail looped])}))
