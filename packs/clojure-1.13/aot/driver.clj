(require '[clojure.edn :as edn] '[clojure.java.io :as io])

(def fixtures ['conformance.hint-values 'conformance.hint-recur])
(defn observe [f]
  (try {:ok (f)}
       (catch Exception e
         (let [chain (take-while some? (iterate ex-cause e))]
           {:phase (some #(-> % ex-data :clojure.error/phase) chain)
            :cause (.getName (class (last chain)))
            :message (.getMessage (last chain))}))))

(def value-row [[:payload] {:fixture/origin :literal} true])
(def expected
  {'conformance.hint-values {:ok {:events [:ordinary :tail 2 1 0 :done]
                                  :observations [value-row value-row value-row]}}
   'conformance.hint-recur {:ok [[:done] {:fixture/origin :runtime} [2 1 0]]}})
(def known {:phase :compile-syntax-check :cause "java.lang.UnsupportedOperationException"
            :message "Can only recur from tail position"})

(let [[mode out classes] *command-line-args*
      report (fn [stage] (io/file out (str stage ".edn")))
      read-stage (fn [stage] (edn/read-string (slurp (report stage))))]
  (if (= mode "compare")
    (let [source (read-stage "source") build (read-stage "compile") loaded (read-stage "classes")]
      (doseq [n fixtures]
        (let [s (source n) b (build n) compiled? (= b {:ok n})
              a (if compiled? (loaded n) b)
              reviewed? (and (= (clojure-version) "1.13.0-alpha11")
                             (= n 'conformance.hint-recur) (= known s b))]
          (prn {:fixture n :source s :compile b :from-classes (loaded n) :known reviewed?})
          (when-not (and (= s a) (or reviewed? (and compiled? (= (expected n) s))))
            (throw (ex-info "Source/AOT property failed" {:fixture n}))))))
    (let [built (when (= mode "classes") (read-stage "compile"))
          rows (into {}
                     (for [n fixtures :when (or (nil? built) (= {:ok n} (built n)))
                           :let [base (.replace (.replace (str n) "." "/") "-" "_")
                                 src (io/resource (str base ".clj"))
                                 cls (io/resource (str base "__init.class"))]]
                       (do
                         (when-not (if (= mode "classes") (and cls (nil? src)) (and src (nil? cls)))
                           (throw (ex-info "Fixture classpath isolation failed"
                                           {:fixture n :mode mode :source src :classes cls})))
                         [n (observe #(if (= mode "compile")
                                        (binding [*compile-path* classes] (compile n))
                                        (do (require n) ((ns-resolve n 'run)))))])))]
      (spit (report mode) (pr-str rows)))))
