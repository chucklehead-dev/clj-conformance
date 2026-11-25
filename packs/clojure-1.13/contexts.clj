(ns conformance.binding-contexts)

(defn runners [binding body]
  {:fn (list 'fn ['inputs] (list 'mapv (list 'fn [binding] body) 'inputs))
   :let (list 'fn ['inputs] (list 'mapv (list 'fn ['x] (list 'let [binding 'x] body)) 'inputs))
   ;; Bind the next sample on the recur edge, so stale locals are observable.
   :loop (list 'fn ['inputs]
               (list 'loop [binding '(first inputs) 'remaining '(next inputs) 'acc []]
                     (list 'let ['out (list 'conj 'acc body)]
                           (list 'if 'remaining
                                 (list 'recur '(first remaining) '(next remaining) 'out)
                                 'out))))})
