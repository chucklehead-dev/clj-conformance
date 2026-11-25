(ns conformance.destructuring-swarm)

(def seed (Long/parseLong (or (first *command-line-args*) "13011")))
(def rng (java.util.Random. seed))
(defn pick [xs] (nth xs (.nextInt rng (count xs))))
(defn coin [] (.nextBoolean rng))
(def families [:keys :strs :syms :ns/keys :rename])

;; Paths and unique locals are the recipe. The compiler below emits syntax;
;; the oracle reads the recipe and input directly.
(defn make-tree [depth prefix profile]
  (mapv
   (fn [i]
     (let [name (str prefix "_" i)
           local (symbol name)
           req? (and (:checked profile) (coin))]
       (if (and (pos? depth) (coin))
         {:key (keyword name) :children (make-tree (dec depth) name profile)
          :required (and (:renamed-required profile) req?)}
         (let [family (pick (:families profile))]
           {:key (case family :strs name :syms local :ns/keys (keyword "ns" name)
                       :rename (keyword (str "source" name)) (keyword name))
            :local local :family family :required req?
            :default? (and (:defaults profile) (not req?) (coin))}))))
   (range (inc (.nextInt rng 3)))))

(defn pattern [nodes]
  (reduce
   (fn [form {:keys [key children local family required default?]}]
     (let [binding (if children (pattern children) local)
           form (cond
                  (or children (= family :rename))
                  (if required (assoc-in form [:req! binding] key) (assoc form binding key))
                  :else
                  (let [directive (if required
                                    (keyword (namespace family) (str (name family) "!"))
                                    family)]
                    (update form directive (fnil conj []) local)))]
       (if default? (assoc-in form [:or local] :fallback) form)))
   {} nodes))

(defn paths [prefix nodes]
  (mapcat (fn [{:keys [key children] :as node}]
            (let [p (conj prefix key)]
              (cons (assoc node :path p) (when children (paths p children)))))
          nodes))

(defn expected [entries m]
  (let [present? (fn [path] (contains? (get-in m (pop path)) (peek path)))
        locals (for [{:keys [local path default?]} entries :when local]
                 (if (or (present? path) (not default?)) (get-in m path) :fallback))
        missing (reduce (fn [acc {:keys [path required]}]
                          (if (and required (not (present? path))) (assoc-in acc path nil) acc))
                        nil entries)]
    [(vec locals) missing]))

(defn input-map [nodes dense?]
  (reduce (fn [m {:keys [key children]}]
            (if (or dense? (coin))
              (assoc m key (if children
                             (when (or dense? (coin)) (input-map children dense?))
                             (pick [nil false 0 :value {}])))
              m))
          (pick [{} {:unrelated false}]) nodes))

;; Every input and default in this generator contains only these data types.
(defn domain-value? [v]
  (or (nil? v) (boolean? v) (keyword? v) (symbol? v) (string? v) (integer? v)
      (and (map? v) (every? domain-value? (mapcat identity v)))))

(def stats (atom {:forms 0 :inputs 0 :max-depth 0}))
(dotimes [trial 64]
  (let [profile {:families (or (seq (filterv (fn [_] (coin)) families)) [:keys])
                 :summaries (filterv (fn [_] (coin)) [:select :all :excess])
                 :defaults (coin) :checked (coin) :renamed-required (coin)}
        tree (make-tree (pick [0 1 2 3]) "k" profile)
        entries (vec (paths [] tree))
        summaries (:summaries profile)
        outputs (zipmap summaries (map (comp symbol name) summaries))
        binding (merge (pattern tree) {:missing 'missing} outputs)
        form (list 'fn [binding] [(vec (keep :local entries)) 'missing outputs])
        context {:seed seed :trial trial :form form :version (clojure-version)}
        f (try (eval form)
               (catch Exception e (prn context) (throw e)))]
    (swap! stats update :forms inc)
    (swap! stats update :max-depth max (apply max (map (comp count :path) entries)))
    (doseq [m (concat [nil {}] (repeatedly 5 #(input-map tree true))
                      (repeatedly 25 #(input-map tree false)))]
      (let [want (expected entries m)
            [locals missing summaries :as got] (try (f m) (catch Exception e (prn (assoc context :input m)) (throw e)))]
        (when-not (= want [locals missing])
          (prn (assoc context :input m :expected want :actual got))
          (throw (ex-info "Destructuring swarm mismatch" {:seed seed :trial trial})))
        (when-not (domain-value? summaries)
          (prn (assoc context :input m :actual summaries))
          (throw (ex-info "Summary contains a value outside the input domain"
                          {:seed seed :trial trial})))
        (when (map? m)
          ;; Adding an unmentioned top-level key only affects :all and :excess.
          (let [extra :conformance/unmentioned
                changed (assoc m extra false)
                want-changed (reduce (fn [s k]
                                       (if (contains? s k) (update s k assoc extra false) s))
                                     summaries [:all :excess])
                expected-change [locals missing want-changed]
                actual-change (try (f changed)
                                   (catch Exception e (prn (assoc context :input changed)) (throw e)))]
            (when-not (= expected-change actual-change)
              (prn (assoc context :input changed :original m
                          :expected expected-change :actual actual-change))
              (throw (ex-info "Unmentioned key changed bindings" {:seed seed :trial trial})))))
        (swap! stats update :inputs inc)))))
(prn (assoc @stats :seed seed :version (clojure-version)))
