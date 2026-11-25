#?(:jank (require (quote conformance.partition))
   :default (load-file "packs/shared/conformance/partition.cljc"))

(let [probes conformance.partition/probes
      rejected (into {} (for [[feature form] probes
                              :let [compiled (try (eval form)
                                                  (catch #?(:jank :default :cljr System.Exception :default Exception) _ nil))]
                              :when (nil? compiled)]
                          [feature true]))
      counts (atom {})
      compile-form (memoize eval)]
  (prn {:probe :partition-features :rejected (vec (keys rejected))})
  (doseq [{:keys [family expected requires] :as c} (conformance.partition/cases)]
    (if (some rejected requires)
      (swap! counts update :skipped (fnil inc 0))
      (let [row (conformance.partition/run-case compile-form c)
            status (cond (= row {:values expected}) :matched
                         (= row (conformance.partition/characterized family "derivative")) :characterized
                         :else :unexpected)]
        (swap! counts update status (fnil inc 0))
        (when (not= status :matched)
          (prn {:case family :status status :expected expected :observed row})))))
  (prn {:partition-results @counts})
  (when (:unexpected @counts) (throw (ex-info "Unexpected partition result" @counts))))
(println "PARTITION-REPLAY-PASSED")
