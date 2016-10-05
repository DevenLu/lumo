(set-env!
 :source-paths    #{"src/cljs"}
 :dependencies '[[org.clojure/clojure        "1.9.0-alpha13"]
                 [org.clojure/clojurescript   "1.9.274"]
                 [org.clojure/tools.reader    "1.0.0-beta3"]
                 [com.cognitect/transit-clj   "0.8.290"        :scope "test"]
                 [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                 [adzerk/boot-cljs            "1.7.228-1"      :scope "test"]
                 [adzerk/boot-cljs-repl       "0.3.3"          :scope "test"]
                 [adzerk/boot-reload          "0.4.12"         :scope "test"]
                 [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                 [weasel                      "0.7.0"          :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl-env start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[boot.util             :as util]
 '[clojure.java.io       :as io])

(deftask check-node-modules []
  (with-pass-thru _
    (let [nm (io/file "node_modules")]
      (if-not (and (.exists nm) (.isDirectory nm))
        (do
          (util/info "Installing node dependencies with `npm install`")
          (dosh "npm" "install"))
        (util/info "Node dependencies already installed, skipping `npm install`")))))

(deftask bundle-js
  [d dev     bool  "Development build"]
  (with-pass-thru _
    (dosh "node" "scripts/build.js" (when dev "--dev"))))

(deftask dev []
  (comp
    (check-node-modules)
    (watch)
    (speak)
    (cljs :compiler-options {:hashbang false
                             :target :nodejs
                             :optimizations :simple
                             :main 'lumo.core
                             :cache-analysis true
                             :source-map nil
                             :verbose true
                             :compiler-stats true
                             :parallel-build true})
    (target)
    (bundle-js :dev true)))

(deftask package-executable []
  (with-pass-thru _
    (dosh "node" "scripts/package")))

(deftask release []
  (comp
    (check-node-modules)
    (speak)
    (cljs :compiler-options {:hashbang false
                             :target :nodejs
                             :optimizations :simple
                             :main 'lumo.core
                             :source-map nil
                             :verbose true
                             :compiler-stats true
                             :parallel-build true})
    (target)
    (bundle-js)
    (package-executable)))
