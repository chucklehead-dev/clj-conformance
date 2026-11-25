;; Shared JVM, CLR and native entry point. Inputs and observations are plain data.
(defn check-case [property input expected actual]
  (when-not (= expected actual)
    (prn {:property property :input input :expected expected :actual actual})
    (throw (ex-info "Derivative property failed" {:property property}))))

(def samples [{} {:k nil} {:k false} {:k 7}])
(def expected-values [:absent nil false 7])

(doseq [[m value] (map vector samples expected-values)]
  (check-case :nested-lookup m value (get-in {:left m} [:left :k] :absent))
  (check-case :local-default m value (let [{x :k :or {x :absent}} m] x)))

;; Rebind the left branch repeatedly, keeping the right branch fixed. Compare
;; all observations in order, including the last transition back to absence.
(doseq [[right rv] (map vector samples expected-values)]
  (let [inputs (mapv (fn [left] {:left left :right right}) (conj samples {}))
        observed (loop [remaining inputs acc []]
                   (if (seq remaining)
                     (let [{{l :k :or {l :absent}} :left
                            {r :k :or {r :absent}} :right} (first remaining)]
                       (recur (next remaining) (conj acc [l r])))
                     acc))]
    (check-case :sibling-history right
                (mapv (fn [lv] [lv rv]) (conj expected-values :absent))
                observed)))

(doseq [left samples right samples]
  (let [calls (atom [])
        collide? (and (contains? left :k) (contains? right :k))
        args [(:k left) (:k right)]
        result (merge-with (fn [a b] (swap! calls conj [a b]) :combined) left right)
        expected (cond collide? {:k :combined}
                       (contains? right :k) right
                       :else left)]
    (check-case :merge-callback [left right]
                [expected (if collide? [args] [])] [result @calls])))

;; Characterize the version-dependent order without assigning a conformance
;; verdict to the direct form. Prebinding establishes an explicit sequence.
(defn default-order [prebound?]
  (let [events (atom [])
        state (atom :before)
        input (fn [] (swap! events conj :input) (reset! state :after) {})
        fallback (fn [] (swap! events conj :default) @state)
        value (if prebound?
                (let [m (input) {:keys [x] :or {x (fallback)}} m] x)
                (let [{:keys [x] :or {x (fallback)}} (input)] x))]
    [value @events]))

(let [direct (default-order false) prebound (default-order true)]
  (check-case :prebound-order {} [:after [:input :default]] prebound)
  (prn {:probe :destructuring-default-order :direct direct :prebound prebound}))

(println "DERIVATIVE-CHECKS-PASSED")
