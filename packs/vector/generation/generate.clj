(require '[hegel.core :as h] '[hegel.generator :as g])
(let [[destination seed] *command-line-args*
      recipe (g/tuple (g/sampled-from [0 1 31 32 33 1023 1024 1025])
                      (g/vector {:min-size 1 :max-size 10}
                                (g/sampled-from [:tail :head :set-first :set-last :push :pop])))
      values (atom [])
      r (h/run-test! {:seed (parse-long seed) :test-cases 100 :phases [:generate]
                      :database "" :verbosity :quiet}
                     (fn [_] (swap! values conj (h/draw! recipe))))]
  (assert (and (:passed? r) (= 100 (count @values))))
  (spit destination (pr-str @values)))
