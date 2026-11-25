(ns conformance.native-regions
  (:require #?(:jolt [jolt.native :refer [native]]
               :clj [conformance.jvm-region :refer [native]])))

(defn accumulate! [^doubles source ^doubles dest ^long steps]
  (native [^doubles source ^doubles dest ^long steps]
    (loop [i 0 total 0.0]
      (if (< i steps)
        (let [next-value (+ (aget source i) (aget dest i))]
          (aset dest i next-value)
          ;; Reading source after the write distinguishes aliased arrays.
          (recur (inc i) (+ total (aget source i))))
        total))))

(defn check-case [values initial steps alias?]
  (let [source (double-array values)
        dest (if alias? source (double-array initial))
        before (if alias? values initial)
        after (mapv (fn [i a b] (if (< i steps) (+ a b) b))
                    (range) values before)
        source-after (if alias? after values)
        expected [(reduce + 0.0 (take steps source-after)) source-after after]
        actual [(accumulate! source dest steps) (vec source) (vec dest)]]
    (when-not (= expected actual)
      (throw (ex-info "Native region disagrees with the vector model"
                      {:values values :initial before :steps steps :alias? alias?
                       :expected expected :actual actual})))
    actual))

(defn -main [& _]
  (doseq [n [0 1 2 7 32]
          sign [-1.0 1.0]
          :let [xs (mapv #(* sign (inc %)) (range n))
                ys (mapv #(- 7.0 %) (range n))]
          steps (distinct [0 (quot n 2) n])
          alias? [false true]]
    (prn [n sign steps alias? (check-case xs ys steps alias?)])))
