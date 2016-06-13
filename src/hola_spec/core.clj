(ns hola-spec.core
  (:require [clojure.spec :as s]))

;; From https://clojure.org/guides/spec

;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;                          PREDICATES
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

;; All the folowing things can be valid predicate specs:
;; - predicate (boolean) fns
;; - sets
;; - registered names of specs
;; - specs (the return values of spec, and, or, keys)
;; - regex ops (the return values of cat, alt, *, +, ?, &)

(s/conform even? 1000)
;;=> 1000

;; conform is the basic operation for consuming specs,
;; and does both validation and conforming/destructuring.
;; Given a spec and a value, returns :clojure.spec/invalid if value
;; does not match spec, else the (possibly destructured) value.

(s/valid? even? 1000)
;;=> true

(s/valid? #(> 20 % 5) 2)
;;=> false

(s/valid? #{42 32 22} 42)
;;=> true

;; valid? Helper function that returns true when x is valid for spec.

;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;                         REGISTRY
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

;; spec provides a CENTRAL REGISTRY for globally declaring reusable specs.
;; The registry associates a namespaced keyword with a specification.

;; def
;; Given a namespace-qualified keyword or symbol k, and a spec, spec-name, predicate or regex-op
;; makes an entry in the registry mapping k to the spec

(s/def ::pares #{2 4 6 8})
;;=> :hola-spec.core/pares

(s/valid? ::pares 2)
;;=> true

;; REMEMBER: keywords with a double semicolon are namespace-qualified
;; keywords with the current namespace.

;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;                     COMPOSING PREDICATES
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

;; The simplest way to compose specs is with and and or

(s/def ::negative-even (s/and integer? even? #(< % 0)))

(s/valid? ::negative-even :foo)
;;=> false

(s/valid? ::negative-even 10)
;;=> false

(s/valid? ::negative-even -10)
;;=> true


(s/def ::integer-or-string (s/or :integer integer?
                              :string  string?))

(s/valid? ::integer-or-string "abc")
;;=> true

(s/valid? ::integer-or-string :a)
;;=> false

(s/valid? ::integer-or-string 2)
;;=> true

;; When we use or, we have to "name" with keywords the differents options we have
;; These names or tags are use to understand or enrich the data return
;; when conform, explain or other spec functions are used

(s/conform ::integer-or-string "abc")
;;=> [:string "abc"]

(s/conform ::integer-or-string 124)
;;=> [:integer 124]

;; Curious!
;; The result of conform in this case is a vector

(def a (s/conform ::integer-or-string 124))

a
;;=> [:integer 124]

(type a)
;;=> clojure.lang.PersistentVector

;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;                           EXPLAIN
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

;; print explanations in console  (to *out*)

(s/explain ::pares 4) ;; Success!

(s/explain ::pares 5) ;; val: 5 fails predicate: #{4 6 2 8}

;; to receive the error messages as a string

(s/explain-str ::integer-or-string :a)
;;=> val: :a fails at: [:integer] predicate: integer?
;;=> val: :a fails at: [:string] predicate: string?

;; to receive the error messages as a data
(s/explain-data ::integer-or-string :a)

;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;                         SEQUENCES
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

;; Spec provides the standard regular expression operators to describe the
;; structure of a SEQUENTIAL DATA VALUE:
;; cat - concatentation of predicates/patterns
;; alt - choice among alternative predicates/patterns
;; * - 0 or more of a predicate/pattern
;; + - 1 or more of a predicate/pattern
;; ? - 0 or 1 of a predicate/pattern

;; Like or, cat and alt tag their "parts"

(s/def ::ingredient (s/cat :quantity number? :unit keyword?))

(s/conform ::ingredient [2 :teaspoon])
;;=> {:quantity 2, :unit :teaspoon}

;; What if we change the order of the arguments?

(s/conform ::ingredient [:teaspoon 2])
;;=> :clojure.spec/invalid

;; Curious!
;; The result of conform in this case is a map

(def b (s/conform ::ingredient [2 :teaspoon]))

b
;;=> {:quantity 2, :unit :teaspoon}

(type b)
;;=> clojure.lang.PersistentArrayMap

;; If we need a description of the spec:

(s/describe ::ingredient)
;;=> (cat :quantity number? :unit keyword?)


(s/def ::odds-then-maybe-even (s/cat :odds (s/+ odd?) ;; at least 1 or more odds
                                     :even (s/? even?))) ;; 0 or 1 even

(s/conform ::odds-then-maybe-even [1 3 5 100])
;;=> {:odds [1 3 5], :even 100}

(s/conform ::odds-then-maybe-even [1])
;;=> {:odds [1]}

(s/explain-str ::odds-then-maybe-even [1 3 6 8])
;;=> "In: [3] val: (8) fails predicate: (cat :odds (+ odd?) :even (? even?)),  Extra input\r\n"


(defn boolean? [b] (instance? Boolean b))
(s/def ::opts (s/* (s/cat :opt keyword? :val boolean?)))

;; s/* allows 0 or more predicate/patterns
(s/conform ::opts [])
;;=> []

(s/conform ::opts [:silent? false])
;;=> [{:opt :silent?, :val false}]

(s/conform ::opts [:silent? false :verbose true])
;;=> [{:opt :silent?, :val false} {:opt :verbose, :val true}]



(s/def ::config (s/* (s/cat :prop string?
                            :val  (s/alt :s string? :b boolean?))))
(s/conform ::config ["-server" "foo" "-verbose" true "-user" "joe"])
;;=> [{:prop "-server", :val [:s "foo"]} {:prop "-verbose", :val [:b true]} {:prop "-user", :val [:s "joe"]}]


;; NESTED
(s/def ::nested
  (s/cat :names-kw #{:names}
         :names (s/spec (s/* string?))
         :nums-kw #{:nums}
         :nums (s/spec (s/* number?))))

(s/conform ::nested [:names ["a" "b"] :nums [1 2 3]])
;;=> {:names-kw :names, :names ["a" "b"], :nums-kw :nums, :nums [1 2 3]}


(s/def ::not-nested
  (s/cat :names-kw #{:names}
         :names (s/* string?)
         :nums-kw #{:nums}
         :nums (s/* number?)))

(s/conform ::not-nested [:names "a" "b" :nums 1 2 3])
;;=> {:names-kw :names, :names ["a" "b"], :nums-kw :nums, :nums [1 2 3]}

;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;                           ENTITY MAPS
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++


;; A common approach in other libraries is to describe each entity type,
;; combining both the keys it contains and the structure of their values.

;; Schema
#_(def Person {:first-name s/String
               :last-name s/String
               :email #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}"
               (s/optional-key :phone) s/String})

;; Specs assign meaning to individual attributes, then collect them into
;; maps using set semantics (on the keys).

;; This approach allows us to start assigning (and sharing) semantics
;; at the attribute level across our libraries and applications!!!

;; clojure.spec
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::email (s/and string? #(re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}" %)))
(s/def ::phone string?)

(s/def ::person
  (s/keys :req [::first-name
                ::last-name
                ::email]
          :opt [::phone]))

;; This registers a ::person spec with the required keys ::first-name, ::last-name, and ::email, with optional key ::phone.
;; The map spec never specifies the value spec for the attributes, only what attributes are required or optional.

;; Why is first-name a keyword????
(type ::first-name)



(s/def ::any-map (s/keys))
(s/conform ::any-map {:a 1 :b 2})
(s/valid? ::any-map {:a 1 :b 2})



















(def fish-numbers {0 "Zero"
                   1 "One"
                   2 "Two"})

(s/def ::fish-number (set (keys fish-numbers)))

;; With (set (keys fish-numbers)) what I get is #{0 1 2}
;; We can use a set as a predicate
