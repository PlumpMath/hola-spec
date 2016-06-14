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
;; maps using set semantics (on the keys). This approach allows us to start
;; assigning (and sharing) semantics at the attribute level across our
;; libraries and applications.

;; clojure.spec
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::email (s/and string? #(re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}" %)))
(s/def ::phone string?)

;; Entity maps in spec are defined with keys

(s/def ::person
  (s/keys :req [::first-name
                ::last-name
                ::email]
          :opt [::phone]))

;; This registers a ::person spec with the required keys ::first-name, ::last-name, and ::email, with optional key ::phone.
;; The map spec never specifies the value spec for the attributes, only what attributes are required or optional.


;; What is a spec object?
(s/spec? ::first-name)
;;=> false
;; I would expect that to be true...
;; because previously I have used s/def to bind ::first-name to a spec, and
;; I expect s/def to work as def, but it doesn´t.

(class ::first-name)
;;=> clojure.lang.Keyword

(s/spec? ::person)
;;=> false

(s/spec? (s/spec (s/cat :even even? :string string?)))
;;=> #object[clojure.spec$regex_spec_impl$reify__11725 0x7416e071 "clojure.spec$regex_spec_impl$reify__11725@7416e071"]

(s/spec? (s/keys :req [::first-name
                       ::last-name
                       ::email]
                 :opt [::phone]))
;;=> #object[clojure.spec$map_spec_impl$reify__11497 0x294ad81a "clojure.spec$map_spec_impl$reify__11497@294ad81a"]


;; s/def doesn´t work as def****************************************************************
;; def locates a global var with the name of a symbol

(def a {})
;;=> #'hola-spec.core/a

;; var is a reference type
;; If we want the reference to a var, we use (var a) or the reader sugar #'a
(var a)
;;=> #'hola-spec.core/a

;;Evaluating a symbol normally results in looking for a var with that name and dereference it.
;; So if we evaluate a:
a
;;=> {}
;; We get the object a is referring to
;; evaluating a is the same as (deref (var a)) or the reader sugar @#'a

;; That´s why when we ask for the class of a, we get the class of the object a is referring to
;; in this case, a clojure.lang.PersistentArrayMap
(class a)
;;=> clojure.lang.PersistentArrayMap

;; On the other hand, s/def just makes a entry in a registry,
;; thus mapping the keyword to the spec.
;; the registry is an (atom {})
;; That´s why, even when a keyword is mapped to a spec using s/def,
;; it is still evaluated as a keyword.
;;******************************************************************************************

;; When conformance is checked on a map, it combines two things:
;; 1- checking that the required attributes are included,
;; 2- and checking that every registered key has a conforming value.

(s/conform ::person {::first-name "Espe" ::last-name "Moreno"})
;;=> :clojure.spec/invalid

(s/explain-str ::person {::first-name "Espe" ::last-name "Moreno"})
;;=> "val: {:hola-spec.core/first-name \"Espe\", :hola-spec.core/last-name \"Moreno\"}
;; fails predicate: [(contains? % :hola-spec.core/email)]\r\n"

(s/conform ::person {::first-name "Espe" ::last-name "Moreno" ::email 123})
;;=> :clojure.spec/invalid

(s/explain-str ::person {::first-name "Espe" ::last-name "Moreno" ::email 123})
;;=> "In: [:hola-spec.core/email] val: 123 fails spec: :hola-spec.core/email at: [:hola-spec.core/email] predicate: string?\r\n"

(s/conform ::person {::first-name "Espe" ::last-name "Moreno" ::email "1@2.com"})
;;=> {:hola-spec.core/first-name "Espe",
;;    :hola-spec.core/last-name "Moreno",
;;    :hola-spec.core/email "1@2.com"}


;; Also note that ALL attributes are checked via keys, not just those listed in the :req and :opt keys.
;; Thus a bare (s/keys) is valid and will check all attributes of a map without checking which keys are required or optional.

(s/def ::any-map (s/keys))
;;=> :hola-spec.core/any-map

(s/conform ::any-map {:a 1 :b 2})
;;=> {:a 1, :b 2}

(s/valid? ::any-map {:a 1 :b 2})
;;=> true
;; It´s going to be true for any clojure-valid map.

(s/describe ::any-map)
;;=> (keys)


;; keys can also specify :req-un and :opt-un for required and optional unqualified keys.




;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;; EXAMPLE from http://swannodette.github.io/2016/06/03/tools-for-thought
;; ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++


;; We are going to define "let". We know, let has some parts:
;; a name, a vector of bindings and some forms. We can start with
(s/def ::let
  (s/cat
     :name     '#{let}
     :bindings ::bindings
     :forms    (s/* (constantly true)))) ;;zero or more forms after the bindings

(s/def ::bindings vector?)
;;=> :hola-spec.core/bindings

(s/conform ::let '(let [x 1] (+ x y)))
;;=> {:name let, :bindings [x 1], :forms [(+ x y)]}

;;We could have just said: (without defining ::bindigs as a separate spec.)
(s/def ::let2
  (s/cat
     :name     '#{let2}
     :bindings vector?
     :forms    (s/* (constantly true))))

(s/conform ::let2 '(let2 [x 1] (+ x y)))
;;=> {:name let2, :bindings [x 1], :forms [(+ x y)]}

;; But ::bindings is going to need to be a little more complex. It´s good
;; to specify it separately
(s/conform ::let '(let [x 1 y] (+ x y)))
;;=> {:name let, :bindings [x 1 y], :forms [(+ x y)]}
;;=> This is not correct, we have to redifine ::bindings

(s/def ::bindings
  (s/and vector? #(even? (count %)))) ;;using thread first macro #(-> % count even?)

(s/conform ::let '(let [x 1 y] (+ x y)))
;;=> :clojure.spec/invalid

(s/conform ::let '(let [1 y] (+ x y)))
;;=> {:name let, :bindings [1 y], :forms [(+ x y)]}

;;Let's make a ::binding spec to control what can appear in the vector:
(s/def ::binding
  (s/cat
    :name  symbol?
    :value (constantly true)))

;; We add another predicate to ::bindings
(s/def ::bindings
  (s/and vector?                ;; It has to be a vector
         #(-> % count even?)    ;; The vector has to have an even number of elements
         (s/* ::binding)))      ;; It has to fulfill the ::binding spec, that is, the first
                                ;; element is a symbol, and the second is a constantly true value.

(s/conform ::let '(let [1 y] (+ x y)))
;;=> :clojure.spec/invalid

(s/conform ::let '(let [a 1] (+ x y)))
;;=> {:name let, :bindings [{:name a, :value 1}], :forms [(+ x y)]}


;; +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;; EXAMPLE from http://gigasquidsoftware.com/blog/2016/05/29/one-fish-spec-fish/
;; +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

(def fish-numbers {0 "Zero"
                   1 "One"
                   2 "Two"})

(s/def ::fish-number (set (keys fish-numbers)))

;; Is the same as to use #{0 1 2} (a set) as a predicate

(s/def ::color #{"Red" "Blue" "Dun"})

;; Specifying the sequences of the values

(s/def ::first-line (s/cat :n1 ::fish-number :n2 ::fish-number :c1 ::color :c2 ::color))

;; What the spec is doing here is associating each part with a tag, to identify what was
;; matched or not, and its predicate/pattern. So, if we try to explain a failing spec,
;; it will tell us where it went wrong.

;; We need to add a spec to check whether the second number is one bigger than the first number.
;; For that we create a function that is goint to take as input the map of
;; the destructured tag keys from the ::first-line

(s/conform ::first-line [1 2 "Red" "Blue"])
;;=> {:n1 1, :n2 2, :c1 "Red", :c2 "Blue"}

;; (one-bigger? {:n1 1, :n2 2, :c1 "Red", :c2 "Blue"})

(defn one-bigger? [{:keys [n1 n2]}]
  (= n2 (inc n1)))

;; Also the colors should be not the same value. We redefine ::first-line:

(s/def ::first-line (s/and (s/cat :n1 ::fish-number :n2 ::fish-number :c1 ::color :c2 ::color)
                           one-bigger?
                           #(not= (:c1 %) (:c2 %))))

(s/valid? ::first-line [1 2 "Red" "Blue"])
;;=> true









(s/def ::binding
  (s/cat
    :name  symbol?
    :value (constantly true)))

(s/def ::bindings
  (s/and vector?
         #(-> % count even?)
         (s/* ::binding)))

(s/def ::let
  (s/cat
     :name     '#{let}
     :bindings ::bindings
     :forms    (s/* (constantly true))))

(s/conform ::let '(let [a 1] (+ x y)))

{:name let, :bindings [{:name a, :value 1}], :forms [(+ x y)]}




(s/def ::fish-number (set (keys fish-numbers)))

(s/def ::color #{"Red" "Blue" "Dun"})

(s/def ::first-line (s/and (s/cat :n1 ::fish-number
                                  :n2 ::fish-number
                                  :c1 ::color
                                  :c2 ::color)
                           one-bigger?
                           #(not= (:c1 %) (:c2 %))))

(s/conform ::first-line [1 2 "Red" "Blue"])

{:n1 1, :n2 2, :c1 "Red", :c2 "Blue"}





