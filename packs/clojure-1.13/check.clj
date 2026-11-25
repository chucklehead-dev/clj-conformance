(ns conformance.alpha-properties)

(def checks (atom {}))
(defn check [property input expected actual]
  (swap! checks update property (fnil inc 0))
  (when-not (= expected actual)
    (let [failure {:property property :input input :expected expected :actual actual}]
      (prn failure)
      (throw (ex-info "Property failed" failure)))))

(defn fragments [k]
  [{} {k nil} {k false} {k 7}])

(def summarize
  (selector {:keys! [a] :keys [b] :or {b 9}
             :select _ :missing _ :excess _ :all _}))

(doseq [a (fragments :a), b (fragments :b), extra (fragments :extra)]
  (let [m (merge a b extra)
        selected (merge {:b 9} a b)
        expected (cond-> {:select selected :all (merge selected extra)}
                   (empty? a) (assoc :missing {:a nil})
                   (seq extra) (assoc :excess extra))]
    (check :selector m expected (summarize m))
    (check :let-bindings m [(get m :a) (get m :b 9) m]
           (let [{:keys! [a] :keys [b] :or {b 9} :missing missing :as orig} m]
             [a b orig]))))

(def nested
  (selector {:req! {{:keys! [x]} :card} :missing _ :select _}))

(doseq [m (concat [nil {}] (for [x (fragments :x), extra (fragments :extra)]
                             {:card (merge x extra)}))]
  (let [card (:card m)
        expected (cond-> {}
                   (some? m) (assoc :select (if card {:card (select-keys card [:x])} {}))
                   (not (contains? card :x)) (assoc :missing {:card {:x nil}}))]
    (check :nested-required m expected (nested m))))

;; CLJ-2990: inspect the actual local, including absence, rather than only
;; observing the selector's summary of missing keys.
(doseq [m [nil {} {:card {}} {:card {:x false}}]]
  (check :required-nested-as m
         [(:card m) (when-not (contains? m :card) {:card nil})]
         (let [{:req! {{:as card} :card} :missing missing} m]
           [card missing])))

(defn required-arg [{:req! {local :acct/id}}] local)
(doseq [id (fragments :acct/id), shadow (fragments :id)]
  (let [m (merge id shadow)
        outcome (try [:value (required-arg m)] (catch Exception _ [:threw]))]
    (check :required-argument m
           (if (contains? m :acct/id) [:value (:acct/id m)] [:threw])
           outcome)))

(defn lookup-model [m path missing]
  (reduce (fn [node k]
            (if (and (map? node) (contains? node k)) (get node k) (reduced missing)))
          m path))

(doseq [node (concat [nil false 7] (fragments :b))
        m [{} {:a node}]
        default [nil false :absent]]
  (let [path [:a :b]
        expected (lookup-model m path default)]
    (check :literal-get-in [m default] expected (get-in m [:a :b] default))
    (check :dynamic-get-in [m default] expected (get-in m (vec (seq path)) default))))

(doseq [left (fragments :x), right (fragments :x)]
  (let [a {:branch (assoc left :left 1)}
        b {:branch (assoc right :right 2)}
        combined (merge (:branch a) (:branch b))
        collided? (and (contains? left :x) (contains? right :x))]
    (check :merge-deep [a b] {:branch combined} (merge-deep a b))
    (check :merge-deep-with [a b]
           {:branch (cond-> combined collided? (assoc :x [(:x left) (:x right)]))}
           (merge-deep-with vector a b))))

(prn {:version (clojure-version) :checks (into (sorted-map) @checks)
      :total (reduce + (vals @checks))})
