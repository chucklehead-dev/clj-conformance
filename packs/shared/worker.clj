(ns conformance.shared-worker
  (:require [clojure.edn :as edn] [clojure.walk :as walk]))

(load-file "packs/shared/conformance/partition.cljc")

(let [[fixtures runtime build out] *command-line-args*
      {:keys [seed cases]} (edn/read-string (slurp fixtures))
      probes (merge conformance.partition/probes {:checked '(fn [{:req! {v :id} :missing m}] [v m])
              :required '(fn [{:req! {{:as v} :child} :missing m}] [v m])})
      rejected (into {} (for [[feature form] probes
                              :let [error (try (eval form) nil (catch Exception e (str e)))]
                              :when error]
                          [feature error]))
      _ (doseq [[feature error] rejected]
          (when-not (or (#{"jvm-baseline" "jvm-stable"} runtime)
                        (and (.startsWith runtime "jolt-")
                             (re-find #"Unsupported map directive: :(req!|missing|select|excess)" error)))
            (throw (ex-info "Unexpected feature rejection"
                            {:runtime runtime :feature feature :error error}))))
      compile-form (memoize eval)
      rows (mapv
            (fn [{:keys [family form inputs requires] :as c}]
              (if-let [missing (seq (filter rejected requires))]
                {:unsupported (vec missing)}
                (if (= :partition (first family))
                  (conformance.partition/run-case compile-form c)
                  (try
                    {:values (walk/postwalk
                            (fn [x] (if (or (nil? x) (boolean? x) (number? x)
                                            (keyword? x) (coll? x) (string? x))
                                      x {:unexpected-class (str (type x))}))
                            ((compile-form form) inputs))}
                    (catch Exception e {:exception (str e)})))))
            cases)]
  (spit out (pr-str {:seed seed :runtime runtime :build build :rejections rejected
                     :ids (mapv (juxt :family :variant) cases) :rows rows}))
  (prn {:runtime runtime :build build :cases (count rows)
        :unsupported (count (filter :unsupported rows))}))
