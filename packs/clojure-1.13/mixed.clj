(ns conformance.mixed-destructuring)

(def seed (Long/parseLong (or (first *command-line-args*) "13011")))
(def rng (java.util.Random. seed))
(defn pick [xs] (nth xs (.nextInt rng (count xs))))
(defn coin [] (.nextBoolean rng))

;; Each layer has exactly one recursively bound child. Map layers add checked
;; and defaulted siblings. Sequence layers place the child at a variable index.
(defn recipe [kind depth]
  (when (pos? depth)
    (let [sym #(symbol (str % depth))]
      (if (= kind :map)
        {:kind :map :key (pick [:items :branch]) :checked (sym "required")
         :defaulted (sym "optional") :missing (sym "missing")
         :child (recipe :sequence (dec depth))}
        {:kind :sequence :index (pick [0 1 2]) :rest (when (coin) (sym "tail"))
         :as (when (coin) (sym "whole")) :child (recipe :map (dec depth))}))))

(defn binding-form [{:keys [kind key checked defaulted missing index child] :as node}]
  (case kind
    :map {(binding-form child) key :keys! [checked] :keys [defaulted]
          :or {defaulted :fallback} :missing missing}
    :sequence (cond-> (conj (vec (repeat index '_)) (binding-form child))
                (:rest node) (conj '& (:rest node))
                (:as node) (conj :as (:as node)))
    'leaf))

(defn observations [{:keys [kind checked defaulted missing child] :as node}]
  (if kind
    (into (vec (remove nil? [checked defaulted missing (:rest node) (:as node)]))
          (observations child))
    ['leaf]))

(defn model [{:keys [kind key checked defaulted index child] :as node} input]
  (case kind
    :map (let [k (keyword checked)]
           (into [(get input k) (get input (keyword defaulted) :fallback)
                  (when-not (contains? input k) {k nil})]
                 (model child (get input key))))
    :sequence (into (cond-> []
                      (:rest node) (conj (seq (drop (inc index) input)))
                      (:as node) (conj input))
                    (model child (nth input index nil)))
    [input]))

(def values [nil false 0 :payload])
(defn specimen [{:keys [kind key checked defaulted index child]} dense?]
  (case kind
    :map (cond-> {}
           (or dense? (coin)) (assoc key (specimen child dense?))
           (or dense? (coin)) (assoc (keyword checked) (pick values))
           (or dense? (coin)) (assoc (keyword defaulted) (pick values)))
    :sequence (let [xs (concat (repeatedly index #(pick values))
                               [(specimen child dense?)]
                               (repeatedly (pick [0 1 2]) #(pick values)))
                    xs (if dense? xs (take (pick (range (+ index 4))) xs))]
                (if (coin) (vec xs) (apply list xs)))
    (pick values)))

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

(dotimes [trial 64]
  (let [tree (recipe (if (even? trial) :map :sequence) (+ 2 (.nextInt rng 4)))
        inputs (vec (concat [nil] (repeatedly 10 #(specimen tree true))
                            (repeatedly 20 #(specimen tree false)) [nil]))
        expected (mapv #(model tree %) inputs)]
    (doseq [[context form] (runners (binding-form tree) (observations tree))]
      (let [replay {:version (clojure-version) :seed seed :trial trial :context context :form form}
            actual (try ((eval form) inputs)
                        (catch Exception e (prn (assoc replay :inputs inputs)) (throw e)))]
        (when-not (= expected actual)
          (let [i (first (keep-indexed #(when (not= %2 (get actual %1)) %1) expected))]
            (prn (assoc replay :inputs inputs :index i :expected expected :actual actual))
            (throw (ex-info "Mixed destructuring mismatch" {:trial trial :context context}))))))))
(prn {:version (clojure-version) :seed seed :bindings 64 :inputs-per-binding 32
      :contexts [:fn :let :loop]})
