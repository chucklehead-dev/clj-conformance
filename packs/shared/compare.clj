(ns conformance.shared-compare
  (:require [clojure.edn :as edn] [clojure.java.io :as io] [clojure.string :as str]))

(defn fields [{:keys [family expected]}]
  (if (= :get-in (first family))
    [[0] [1]]
    (into [[:outer]] (for [[side obs] (first expected) :when (map? obs)
                           :when (not= :outer side) field (keys obs)] [side field]))))

(defn differences [c a b]
  (if (and (:values a) (:values b))
    (when (not= a b)
      (let [project (fn [row path] (mapv #(get-in % path) (:values row)))
            changed (filter #(not= (project a %) (project b %)) (fields c))]
        (or (seq changed) [[:shape]])))
    (when (not= a b) [[:exception]])))

(defn findings [c reports i]
  (let [runs (for [{:keys [runtime rows]} reports
                   :let [row (nth rows i)] :when (not (:unsupported row))]
               [runtime row])
        pairs (for [[a x] runs [b y] runs :when (neg? (compare a b))] [a x b y])
        agreed? (and (seq runs) (apply = (map second runs)))
        comparisons (if agreed?
                      (let [[_ row] (first runs)]
                        [[(mapv first runs) row :model {:values (:expected c)}]])
                      pairs)]
    (for [[a x b y] comparisons path (differences c x y)]
      {:key [(:family c) a b path] :case i :a x :b y})))

(defn case-group [{:keys [family requires]}]
  (if (= :get-in (first family))
    :lookup
    (if (seq requires) :required :ordinary)))

(defn coverage-table [seed cases reports]
  (let [lines
        (for [{:keys [runtime build rows]} reports]
          (let [groups (group-by (comp case-group first) (map vector cases rows))
                cell (fn [group]
                       (let [counts (frequencies
                                     (for [[c row] (groups group)]
                                       (cond (:unsupported row) :skip
                                             (= {:values (:expected c)} row) :pass
                                             :else :differ)))]
                         (str/join " / " (map #(get counts % 0) [:pass :skip :differ]))))
                label (if (str/starts-with? runtime "jvm-") (str runtime " " build) runtime)]
            (str "| " label " | " (cell :lookup) " | " (cell :ordinary) " | " (cell :required) " |")))]
    (str/join "\n"
              (concat ["## Shared property results" ""
                       (str "Seed `" seed "`. Each cell counts cases that matched the model / "
                            "skipped a required feature / differed from the model.")
                       "" "| Worker | Nested lookup | Ordinary sibling bindings | Required sibling bindings |"
                       "| --- | --- | --- | --- |"]
                      lines ["" "Build identities and failures are in `comparison.edn`."]))))

(let [[fixtures out & files] *command-line-args*
      {:keys [cases seed]} (edn/read-string (slurp fixtures))
      reports (mapv #(edn/read-string (slurp %)) files)
      names (map :runtime reports)
      dir (io/file out)]
  (when-not (and (seq files) (= (count names) (count (distinct names)))
                 (every? #(and (= seed (:seed %)) (= (count cases) (count (:rows %)))
                               (= (mapv (juxt :family :variant) cases) (:ids %))) reports))
    (throw (ex-info "Missing, duplicate, or incompatible worker reports" {:workers names})))
  (.mkdirs dir)
  (let [all (mapcat (fn [i] (findings (cases i) reports i)) (range (count cases)))
        grouped (group-by :key all)
        evidence
        (mapv (fn [n [signature hits]]
                (let [best (apply min-key
                                  (fn [{:keys [case]}]
                                    (count (pr-str ((juxt :form :inputs) (cases case)))))
                                  hits)
                      c (cases (:case best))
                      file (str "repro-" n ".clj")]
                  (spit (io/file dir file)
                        (str (pr-str (list 'prn (list (:form c) (list 'quote (:inputs c))))) "\n"))
                  (assoc best :signature signature :reproducer file :expected (:expected c))))
              (range) (sort-by (comp pr-str key) grouped))
        summary {:seed seed :cases (count cases)
                 :workers (mapv (fn [r] (assoc (select-keys r [:runtime :build :rejections])
                                               :executed (count (remove :unsupported (:rows r)))))
                                reports)
                 :findings evidence}]
    (spit (io/file dir "coverage.md") (coverage-table seed cases reports))
    (spit (io/file dir "comparison.edn") (pr-str summary))
    (prn (assoc (dissoc summary :findings :workers) :workers names :findings (count evidence)))
    (when (seq evidence) (System/exit 1))))
