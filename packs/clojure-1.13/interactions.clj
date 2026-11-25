(ns conformance.jvm-interactions)

(def version (clojure-version))
(def candidate? (= 13 (:minor *clojure-version*)))
(def seed (Long/parseLong (or (first *command-line-args*) "13011")))
(def rng (java.util.Random. seed))
(def totals (atom {}))
(def unexpected (atom 0))

(defn check
  ([property input want got] (check property input want got nil))
  ([property input want got reviewed]
   (let [status (cond (= want got) :pass
                      (and (= version "1.13.0-alpha11") (some? reviewed) (= reviewed got)) :known
                      :else :fail)]
     (swap! totals update-in [property status] (fnil inc 0))
     (when (= status :fail) (swap! unexpected inc))
     (when (not= status :pass)
       (binding [*print-meta* true]
         (prn {:status status :property property :input input :expected want :observed got}))))))

(defn observe [form]
  (try [:value (eval form)]
       (catch Exception e
         (let [cause (last (take-while some? (iterate ex-cause e)))]
           [:error (:clojure.error/phase (ex-data e)) (.getName (class cause)) (.getMessage cause)]))))

;; The *second* key expression mutates the source before an ordinary call can
;; read the first key. Use a fresh map and event log for each call shape.
(defn lookup [shape before after]
  (let [m (doto (java.util.HashMap.) (.put :a {:b before}))
        events (atom [])
        key! (fn [] (swap! events conj :key) (.put m :a {:b after}) :b)
        value (case shape
                :inline (get-in m [:a (key!)])
                :bound (let [ks [:a (key!)]] (get-in m ks))
                :var (#'get-in m [:a (key!)]))]
    [value @events]))

(doseq [before [nil false :old] after [nil false :new] shape [:inline :bound :var]]
  (check :two-argument-get-in [shape before after] [after [:key]] (lookup shape before after)
         (when (= shape :inline) [before [:key]])))

(defmacro result-of [expr] expr)
(doseq [wrapper ['if-let 'when 'result-of] hint [nil 'String 'Object]]
  (let [branch '(if (pos? i) (recur (dec i)) "done")
        body (case wrapper
               if-let '(if-let [more (pos? i)] (recur (dec i)) "done")
               when (list 'when true branch)
               result-of (list 'result-of branch))
        body (if hint (with-meta body {:tag hint}) body)
        form (list 'loop ['i 2] body)]
    (check :tail-position form [:value "done"] (observe form)
           (when hint [:error :compile-syntax-check "java.lang.UnsupportedOperationException"
                       "Can only recur from tail position"]))))

;; Compare the result, thrown data and effect order. The receiver has a single
;; applicable zero-argument method, so overload selection cannot explain a change.
(doseq [tag [nil 'String] throw? [true false]]
  (let [local (with-meta 's (when tag {:tag tag}))
        expr (list 'let [local '(do (swap! events conj :receiver) "abc")]
                   '(swap! events conj :call)
                   (if throw? '(throw (ex-info "probe" {:kind :probe})) '(.length s)))
        form (list 'let ['events '(atom [])]
                   [(list 'try expr '(catch clojure.lang.ExceptionInfo e (ex-data e)))
                    '(deref events)])]
    (check :local-hint-effects form [:value [(if throw? {:kind :probe} 3) [:receiver :call]]]
           (observe form))))

(when candidate?
  (doseq [k [nil false 0 :k] dir [nil :select :all] input [{} {k nil} {k false} {k 4}]]
    (let [b (cond-> {'x k :or {'x 7}} dir (assoc dir 'view))
          form (list 'let [b input] (if dir '[x view] 'x))
          x (get input k 7)]
      (check :key-renaming form [:value (if dir [x {k x}] x)] (observe form)
             (when (and dir (not k))
               [:error :macro-syntax-check "java.lang.IllegalArgumentException"
                "symbol x in :or does not refer to a binding"])))))

;; Syntax metadata belongs to the call form. A macro returning its argument
;; must still return the same object, including that object's runtime metadata.
(doseq [tag [nil 'clojure.lang.IPersistentVector 'Object]]
  (let [call (with-meta '(result-of (do (swap! hits inc) v))
               (cond-> {:test/annotation :syntax} tag (assoc :tag tag)))
        form (list 'let ['hits '(atom 0) 'v '(with-meta [1] {:test/annotation :value})
                         'out call]
                   '[(identical? v out) (meta out) @hits])]
    (check :syntax-and-value-metadata form [:value [true {:test/annotation :value} 1]] (observe form))))

(def metadata-samples [nil {} {:source false :nested {:marker nil}}])
(doseq [v [[1] '(1) {:k false} #{:k} 'demo] md metadata-samples]
  (let [old (with-meta v {:old true})
        new (with-meta old md)]
    (check :vary-meta [v md]
           [(assoc md :extra 1) md {:old true}]
           [(meta (vary-meta new assoc :extra 1)) (meta new) (meta old)])
    (check :metadata-as-map-key [v md]
           [true (hash old) :found true {:old true} md]
           [(= old new) (hash new) (get {old :found} new) (contains? #{old} new)
            (meta old) (meta new)])))

(doseq [[to additions] [[[] [1 2]] [#{} [:a :b]] [{} [[:a nil] [:b false]]]]
        md metadata-samples]
  (let [original (with-meta to md)
        result (reduce conj to additions)]
    (doseq [out [(into original additions) (into original (map identity) additions)]]
      (check :into-metadata [to additions md] [result md to md]
             [out (meta out) original (meta original)]))
    (when candidate?
      (let [out (persistent! (reduce conj! (transient original) additions))]
        (check :transient-metadata [to additions md] [result md] [out (meta out)])))))

(defn shuffled [xs]
  (let [items (java.util.ArrayList. xs)]
    (java.util.Collections/shuffle items rng)
    (vec items)))

(defn history [size]
  (let [ks (shuffled (mapv #(keyword (str "k" %)) (range size)))
        deleted (take-nth 2 ks)
        mixed [nil false "other" 1]]
    (concat (map-indexed (fn [i k] [:put k (nth [nil false i] (mod i 3))]) ks)
            (map #(vector :drop %) deleted)
            (map #(vector :put % false) mixed)
            (map #(vector :put % nil) (reverse deleted))
            (map #(vector :drop %) mixed))))

(doseq [n [7 8 9 63 64 65] :let [ops (vec (history n))] md metadata-samples]
  (let [origin (with-meta (array-map) md)
        ks (distinct (cons :absent (map second ops)))]
    (loop [t (transient origin) model {} i 0 snapshots []]
      (if-let [[op k v] (get ops i)]
        (let [t (if (= op :put) (assoc! t k v) (dissoc! t k))
              m (if (= op :put) (assoc model k v) (dissoc model k))
              witness [seed n md i [op k v]]]
          (check :map-count witness (count m) (count t))
          (doseq [q ks]
            (check :map-observations [witness q]
                   [(contains? m q) (find m q) (get m q ::absent)]
                   [(contains? t q) (find t q) (get t q ::absent)]))
          (if (#{n (+ n (quot (inc n) 2))} (inc i))
            (let [snapshot (persistent! t)]
              (check :map-checkpoint witness m snapshot)
              (when candidate? (check :map-checkpoint-meta witness md (meta snapshot)))
              (recur (transient snapshot) m (inc i) (conj snapshots [snapshot m])))
            (recur t m (inc i) snapshots)))
        (let [out (persistent! t)
              updated (update-vals (with-meta out md) (fn [v] [v]))]
          (check :map-regrowth [seed n] model out)
          (check :update-vals [seed n md] [(into {} (map (fn [[k v]] [k [v]]) model)) md]
                 [updated (meta updated)])
          (when candidate? (check :map-final-meta [seed n] md (meta out)))
          (doseq [[snapshot before] snapshots]
            (check :snapshot-isolation [seed n] before snapshot))
          (check :map-origin [seed n] [{} md] [origin (meta origin)]))))))

(prn {:version version :seed seed :checks @totals})
(when (pos? @unexpected) (System/exit 1))
