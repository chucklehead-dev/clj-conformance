(require '[clojure.edn :as edn])

;; Oracle state is an ordered seq. Materialize it before retaining a snapshot.
(defn step [state op broken?]
  (let [[v m offset] state n (count m)]
    (case op
      :push [(conj v :marker) (concat m [:marker]) false]
      :pop (if (pos? n) [(pop v) (butlast m) offset] state)
      :tail (if (pos? n) [(subvec v 1 n) (rest m) true] state)
      :head (if (pos? n) [(subvec v 0 (dec n)) (butlast m) offset] state)
      (:set-first :set-last)
      (if (zero? n) state
          (let [i (if (= op :set-first) 0 (dec n))]
            ;; Negative control: a write to a nonzero-offset view misses its slot.
            [(if (and broken? offset) v (assoc v i :updated))
             (map-indexed (fn [j x] (if (= i j) :updated x)) m) offset])))))

(defn mismatch [v m]
  (cond
    (not= (count m) (count v)) :count
    (not= (seq m) (seq v)) :contents
    (not= (last m) (nth v (dec (count m)) nil)) :indexed-read
    (not= (reverse m) (reduce conj '() v)) :reduce-order
    (not= (next m) (seq (drop 1 v))) :drop))

(defn observe [[size ops] broken?]
  (loop [v (vec (range size)) m (range size) offset false
         saved [] remaining ops]
    (when-let [op (first remaining)]
      (let [[w model shifted] (step [v m offset] op broken?)
            expected (doall model)
            snapshots (conj saved [v m])
            law (or (mismatch w expected)
                    (when (some (fn [[s e]] (mismatch s e)) snapshots) :persistence))]
        (if law {:law law :op op}
            (recur w expected shifted snapshots (next remaining)))))))

(defn minimize [[n ops :as recipe] broken? failure]
  (let [candidates (concat (for [i (range (count ops))]
                             [n (into (subvec ops 0 i) (subvec ops (inc i)))])
                           (for [k [0 1 2 3 31 32 33] :when (< k n)] [k ops]))]
    (if-let [smaller (first (filter #(= failure (observe % broken?)) candidates))]
      (recur smaller broken? failure) recipe)))

(let [[mode file] *command-line-args*]
  (case mode
    "control"
    (let [recipe [33 [:head :tail :set-first :push]]
          failure (observe recipe true)
          minimum (minimize recipe true failure)]
      (assert (= {:law :contents :op :set-first} failure))
      (assert (= [2 [:tail :set-first]] minimum))
      (assert (nil? (observe minimum false)))
      (prn [minimum]))
    "replay-control"
    (let [[recipe] (edn/read-string (slurp file))]
      (assert (= {:law :contents :op :set-first} (observe recipe true)))
      (assert (nil? (observe recipe false))))
    "check"
    (let [recipes (edn/read-string (slurp file))]
      (assert (seq recipes))
      (doseq [r recipes]
        (when-let [failure (observe r false)]
          (let [minimum (minimize r false failure)]
            (prn {:failure failure :minimum minimum})
            (throw (ex-info "Vector contract failed" {:recipe minimum :failure failure})))))
      (prn {:passed (count recipes) :sizes (sort (distinct (map first recipes)))
            :operations (frequencies (mapcat second recipes))}))))
