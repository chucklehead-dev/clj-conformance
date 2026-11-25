(ns conformance.hint-recur)

(defmacro pass [expr] expr)

(defn run []
  (let [v (with-meta [:done] {:fixture/origin :runtime})
        events (atom [])]
    (loop [i 2]
      ^clojure.lang.IPersistentVector
      (pass (do (swap! events conj i)
                (if (pos? i)
                  (recur (dec i))
                  [v (meta v) @events]))))))
