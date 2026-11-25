(ns conformance.jvm-region)

(defmacro native [_parameters & body]
  `(do ~@body))
