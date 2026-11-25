;; The literal and runtime paths must remain separate for compiler comparisons.
(defn runtime-results [x]
  [(bit-and x 31) (mod x 32) (quot x 32)])

(let [literal [(bit-and -33 31) (mod -33 32) (quot -33 32)]
      runtime (runtime-results (Long/parseLong "-33"))]
  (prn [literal runtime])
  (when-not (= [31 31 -1] literal runtime)
    (throw (ex-info "Numeric boundary mismatch"
                    {:expected [31 31 -1] :literal literal :runtime runtime}))))
