(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :source-paths    #{"src/cljs"}
 :resource-paths  #{"src/js"}
 :dependencies '[[org.clojure/clojurescript   "1.9.229"        :scope "provided"]
                 [com.cognitect/transit-clj   "0.8.288"        :scope "test"]
                 [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                 [adzerk/boot-cljs            "1.7.228-1"      :scope "test"]
                 [adzerk/boot-cljs-repl       "0.3.3"          :scope "test"]
                 [adzerk/boot-reload          "0.4.12"         :scope "test"]
                 [adzerk/bootlaces            "0.1.13"         :scope "test"]
                 [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                 [weasel                      "0.7.0"          :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl-env start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.bootlaces      :refer [bootlaces! push-release]]
 '[clojure.java.io :as io])

(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
  pom {:project 'plomber
       :version +version+
       :description "Component instrumentation for Om Next"
       :url "http://github.com/anmonteiro/plomber"
       :scm {:url "https://github.com/anmonteiro/plomber"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask add-node-modules []
  (with-pre-wrap fileset
    (let [nm (io/file "node_modules")]
      (when-not (and (.exists nm) (.isDirectory nm))
        (dosh "npm" "install" "react"))
      (-> fileset
        (add-resource (io/file ".") :include #{#"^node_modules/"})
        commit!))))

(deftask webpack []
  (with-pass-thru _
    (dosh "npm" "run" "build")))

(deftask dev []
  (comp
    (add-node-modules)
    (watch)
    (speak)
    (cljs :source-map true
          :compiler-options {:hashbang false
                             :target :nodejs
                             :optimizations :simple
                             :main 'lumo.core
                             :output-dir "out"
                             :compiler-stats true
                             :verbose true
                             :parallel-build true
                             :output-to "out/main.js"})
    (target)
    (webpack)))
