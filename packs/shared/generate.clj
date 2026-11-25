(ns conformance.shared-generator)
(load-file "packs/clojure-1.13/siblings.clj")
(require '[conformance.sibling-destructuring :as siblings])

(load-file "packs/shared/conformance/partition.cljc")

(defn sibling-case [recipe inputs context]
  {:recipe recipe :inputs inputs :form (siblings/runner-form recipe context)
   :context context :expected (mapv #(siblings/model recipe %) inputs)
   :requires (vec (filter (fn [k] (some k (vals recipe))) [:checked :required]))})

(defn sibling-family [trial context recipe inputs]
  ;; Run reductions on every worker so the comparator can verify the same pair.
  (let [variants (concat [[recipe inputs]]
                         (for [s siblings/sides
                               :let [r {s (recipe s)}]]
                           [r (mapv #(select-keys % [s]) inputs)])
                         (for [s siblings/sides
                               :let [r {s (assoc (recipe s) :rows false :capture true)}]
                               xs [[{}] [{} {s {}}]]]
                           [r xs]))]
    (map-indexed (fn [i [r xs]]
                   (assoc (sibling-case r xs context) :family [:siblings trial context] :variant i))
                 variants)))

(defn get-in-case [context m default]
  (let [path (if (= context :literal) [:a :b] '(mark :path path))
        form (list 'fn ['inputs]
                   (list 'mapv
                         (list 'fn [['m 'path 'default]]
                               (list 'let ['events '(atom [])
                                           'mark '(fn [label x] (swap! events conj label) x)
                                           'value (list 'get-in '(mark :map m) path '(mark :default default))]
                                     ['value '(deref events)]))
                         'inputs))
        leaf (:a m)
        expected (if (and (map? leaf) (contains? leaf :b)) (:b leaf) default)]
    {:family [:get-in context m default] :variant 0 :context context :requires []
     :form form :inputs [[m [:a :b] default]]
     :expected [[expected (if (= context :literal) [:map :default] [:map :path :default])]]}))

(let [bindings (for [trial (range 12)
                     :let [r (siblings/trial-recipe trial)
                           ;; Guarantee ordinary and feature-specific forms in every corpus.
                           r (into {} (for [[s opts] r]
                                        [s (case (mod trial 4)
                                             0 (assoc opts :checked false :required false)
                                             1 (assoc opts :checked true :required false)
                                             2 (assoc opts :checked false :required true)
                                             opts)]))
                           xs (siblings/history r)]
                     context [:fn :let :loop]]
                 (sibling-family trial context r xs))
      lookups (for [context [:literal :computed]
                    m [nil {} {:a nil} {:a false} {:a {}} {:a {:b nil}} {:a {:b false}} {:a {:b 7}}]
                    default [nil false :absent]]
                (get-in-case context m default))]
  (spit (nth *command-line-args* 2)
        (pr-str {:seed siblings/seed :cases (vec (concat (apply concat bindings) lookups (conformance.partition/cases)))})))
