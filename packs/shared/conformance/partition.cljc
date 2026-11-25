(ns conformance.partition)

(def probes
  {:select '(fn [{x :a :select picked}] [x picked])
   :excess '(fn [{x :a :excess unused}] [x unused])})

(defn case-form [shape behavior nested? default? dirs]
  (let [key-expr (case shape :constant :a :prebound 'k :inline '(key!))
        leaf (cond-> {'x key-expr} default? (assoc :or {'x 7}))
        binding (merge (if nested? {leaf :branch} leaf) dirs)
        choose (case behavior
                 :alternate '(if (odd? n) :a :b)
                 :throw-on-repeat '(if (= n 1) :a (throw (ex-info "Repeated key" {:partition/repeat true})))
                 :same :a)
        bindings (cond-> ['calls '(atom 0) 'key! (list 'fn [] (list 'let ['n '(swap! calls inc)] choose))]
                   (= shape :prebound) (conj 'k '(key!)))]
    (list 'fn ['inputs]
          (list 'mapv (list 'fn ['m]
                            (list 'let (conj bindings binding 'm)
                                  ['x dirs '(deref calls)]))
                'inputs))))

(defn prediction [shape nested? default? dirs m]
  (let [branch (if nested? (:branch m) m)
        local (get branch :a (when default? 7))
        selected (if (or (contains? branch :a) default?) {:a local} {})
        excess (dissoc branch :a)
        unused (if nested?
                 (cond-> (dissoc m :branch) (seq excess) (assoc :branch excess))
                 excess)]
    [local (cond-> {} (:select dirs) (assoc :select (if nested? {:branch selected} selected))
                      (:excess dirs) (assoc :excess (not-empty unused)))
     (if (= shape :constant) 0 1)]))

(defn cases []
  (vec
   (for [[nested? default?] [[false false] [true true]]
         shape (if nested? [:constant :prebound] [:constant :prebound :inline])
         behavior (if (or nested? (= shape :constant)) [:same] [:same :alternate :throw-on-repeat])
         dirs [{} {:select 's} {:excess 'e} {:select 's :excess 'e}]
         :let [inputs (if nested?
                        (mapv (fn [branch] {:branch branch :outer 30})
                              [{:a 10 :b 20} {:a nil :b 20} {:a false} {:b 20} {}])
                        [{:a 10 :b 20}])]]
     {:family [:partition shape behavior nested? (vec (keys dirs))]
      :variant 0 :form (case-form shape behavior nested? default? dirs)
      :inputs inputs :requires (vec (keys dirs))
      :expected (mapv #(prediction shape nested? default? dirs %) inputs)})))

;; Exact alternate observations reproduced by JVM alpha11 and Babashka. Keep
;; these visible alongside the single-evaluation model pending contract review.
(defn characterized [[_ shape behavior nested? dirs] runtime]
  (when (and (= shape :inline) (not nested?) (seq dirs))
    (cond
      (= behavior :throw-on-repeat) {:threw :repeated-key}
      (and (#{"jolt-release" "jolt-nightly"} runtime) (= dirs [:select]))
      {:values [[10 {:select {:a 10}} 3]]}
      :else
      (let [alternates? (= behavior :alternate)
            picked (if alternates? {:b 20} {:a 10})
            unused (if (and alternates? (= dirs [:excess])) {:a 10} {:b 20})
            result (cond-> {} (some #{:select} dirs) (assoc :select picked)
                              (some #{:excess} dirs) (assoc :excess unused))]
        {:values [[10 result (inc (count dirs))]]}))))

(defn run-case [compile-form {:keys [form inputs]}]
  (try {:values ((compile-form form) inputs)}
       (catch #?(:jank :default :cljr System.Exception :default Exception) e
         (if (:partition/repeat (ex-data e))
           {:threw :repeated-key}
           (throw e)))))
