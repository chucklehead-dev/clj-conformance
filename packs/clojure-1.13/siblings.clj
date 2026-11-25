(ns conformance.sibling-destructuring)

(load-file (str (.getParent (java.io.File. *file*)) "/contexts.clj"))

(def seed (Long/parseLong (or (first *command-line-args*) "13011")))
(def rng (java.util.Random. seed))
(defn pick [xs] (nth xs (.nextInt rng (count xs))))
(def sides [:left :right])

(defn branch [side {:keys [index required capture rows checked default]}]
  (let [local #(symbol (str (name side) "_" (name %)))
        id (local :id), missing (local :missing), raw (local :raw)
        leaf (if checked {:req! {id :id} :missing missing} {id :id})
        leaf (cond-> leaf default (assoc (local :label) :label
                                         :or {(local :label) :fallback}))
        row (conj (vec (repeat index '_)) leaf)]
    {:binding (cond-> {} rows (assoc row :rows) capture (assoc :as raw))
     :required required
     :body (cond-> {} capture (assoc :raw raw) rows (assoc :id id)
                   (and rows checked) (assoc :missing missing)
                   (and rows default) (assoc :label (local :label)))}))

(defn runner-form [recipe context]
  (let [branches (into {} (for [[side opts] recipe] [side (branch side opts)]))
        required? (some :required (vals branches))
        binding (reduce-kv (fn [b side {:keys [binding required]}]
                             (if required (assoc-in b [:req! binding] side)
                                 (assoc b binding side)))
                           (if required? {:missing 'missing} {}) branches)
        body (into {:outer (when required? 'missing)} (for [[side b] branches] [side (:body b)]))]
    (get (conformance.binding-contexts/runners binding body) context)))

(defn model [recipe input]
  (reduce-kv
   (fn [result side {:keys [required capture rows checked default index]}]
     (let [raw (get input side)
           row (nth (:rows raw) index nil)]
       (cond-> (assoc result side
                      (cond-> {} capture (assoc :raw raw) rows (assoc :id (:id row))
                              (and rows checked) (assoc :missing (when-not (contains? row :id) {:id nil}))
                              (and rows default) (assoc :label (get row :label :fallback))))
         (and required (not (contains? input side))) (assoc-in [:outer side] nil))))
   {:outer nil} recipe))

(defn discrepancy [recipe inputs outputs]
  (first
   (for [i (range (count inputs))
         :let [input (inputs i) actual (outputs i) expected (model recipe input)]
         side (concat (keys recipe) [:outer])
         field (if (= side :outer) [nil] (keys (expected side)))
         :let [path (if field [side field] [side])
               got (get-in actual path)
               stable? (and field (pos? i) (= 2 (count recipe))
                            (not= input (inputs (dec i)))
                            (= (find input side) (find (inputs (dec i)) side)))
               prior (when stable? (get-in (outputs (dec i)) path))
               want (get-in expected path)]
         :when (or (and stable? (not= prior got)) (not= want got))]
     (if (and stable? (not= prior got))
       {:signature [:isolation side field] :index i :expected prior :actual got}
       {:signature [:model side field] :index i :expected want :actual got}))))

(defn smaller-values [v]
  (cond
    (map? v) (concat (for [k (keys v)] (dissoc v k))
                     (for [[k item] v, smaller (smaller-values item)] (assoc v k smaller)))
    (vector? v) (concat (for [i (range (count v))]
                          (into (subvec v 0 i) (subvec v (inc i))))
                        (for [[i item] (map-indexed vector v), smaller (smaller-values item)]
                          (assoc v i smaller)))
    (some? v) [nil]))

(defn simpler [[recipe inputs]]
  (concat
   (when (> (count inputs) 1)
     (for [i (range (count inputs))]
       [recipe (into (subvec inputs 0 i) (subvec inputs (inc i)))]))
   (when (> (count recipe) 1)
     (for [side (keys recipe)] [(dissoc recipe side) inputs]))
   (for [[side opts] recipe, flag [:required :checked :default :capture :rows]
         :when (and (opts flag)
                    (or (not (#{:rows :capture} flag))
                        (opts (if (= flag :rows) :capture :rows))))]
     [(assoc-in recipe [side flag] false) inputs])
   (for [[side opts] recipe :when (pos? (:index opts))]
     [(assoc-in recipe [side :index] 0) inputs])
   (for [i (range (count inputs)), smaller (smaller-values (inputs i))]
     [recipe (assoc inputs i smaller)])))

(defn minimize [witness reproduces?]
  (loop [current witness]
    (if-let [smaller (first (filter reproduces? (simpler current)))]
      (recur smaller)
      current)))

(defn exercise [recipe inputs context compile-form]
  (let [runner (memoize (fn [r] (compile-form (runner-form r context))))
        failure (fn [[r xs]] (discrepancy r xs ((runner r) xs)))]
    (when-let [original (failure [recipe inputs])]
      (let [[r xs :as reduced] (minimize [recipe inputs]
                                         #(= (:signature original) (:signature (failure %))))]
        {:context context :form (runner-form r context) :inputs xs :recipe r
         :failure (failure reduced)}))))

(defn history [recipe]
  (let [row #(hash-map :id (pick [nil false 0 7]) :label (pick [nil false :value]))
        initial (into {} (for [side sides] [side {:rows (vec (repeatedly 3 row))}]))]
    (vec (reductions
          (fn [m [side op]]
            (let [i (get-in recipe [side :index])]
              (case op
                :reset (assoc m side (initial side))
                :drop (dissoc m side)
                :nil (assoc m side nil)
                :truncate (assoc m side {:rows []})
                :remove-id (assoc m side (update-in (initial side) [:rows i] dissoc :id))
                :nil-id (assoc m side (assoc-in (initial side) [:rows i :id] nil)))))
          initial (for [op [:remove-id :nil-id :truncate :nil :drop :reset]
                        side (if (.nextBoolean rng) sides (reverse sides))]
                    [side op])))))

(def probes
  {:checked ['(fn [{:req! {v :id} :missing m}] [v m])
             [{} {:id false}] [[nil {:id nil}] [false nil]]]
   :required ['(fn [{:req! {{:as v} :child} :missing m}] [v m])
              [{} {:child {}}] [[nil {:child nil}] [{} nil]]]})

(defn probe [[form inputs want]]
  (let [compiled (try (eval form) (catch Exception e
                                    (if (re-find #"Unsupported map directive: :(req!|missing)" (str e))
                                      (do (prn {:rejected form :reason (str e)}) nil)
                                      (throw e))))]
    (when compiled
      (let [actual (mapv compiled inputs)]
        (when-not (= want actual)
          (throw (ex-info "Accepted feature probe returned an unexpected result"
                          {:form form :expected want :actual actual})))
        true))))

(def supported
  (if (= "--probe" (second *command-line-args*))
    (into {} (for [[feature p] probes] [feature (boolean (probe p))]))
    {:checked true :required true}))

(defn trial-recipe [trial]
  (into {} (for [side sides]
             [side {:rows true :capture true :index (pick [0 1 2])
                    :checked (and (:checked supported) (or (zero? trial) (.nextBoolean rng)))
                    :required (and (:required supported) (or (zero? trial) (.nextBoolean rng)))
                    :default (.nextBoolean rng)}])))

(dotimes [trial 32]
  (let [r (trial-recipe trial) inputs (history r)]
    (doseq [context [:fn :let :loop]]
      (try
        (when-let [report (exercise r inputs context eval)]
          (prn (assoc report :seed seed :trial trial :version (clojure-version)))
          (throw (ex-info "Sibling destructuring mismatch" {:context context})))
        (catch Exception e
          (prn {:seed seed :trial trial :context context :original-form (runner-form r context)
                :original-inputs inputs})
          (throw e))))))
(prn {:seed seed :version (clojure-version) :features supported :bindings 32 :inputs-per-binding 13
      :contexts [:fn :let :loop]})
