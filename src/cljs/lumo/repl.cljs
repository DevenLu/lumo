(ns lumo.repl
  (:require [cljs.analyzer :as ana]
            [cljs.env :as env]
            [cljs.js :as cljs]
            [cljs.nodejs :as nodejs]
            [cljs.reader :as reader]
            [cljs.tools.reader :as r]
            [clojure.string :as string]
            [cognitect.transit :as transit]))

;; =============================================================================
;; Globals

(defonce ^:private st (cljs/empty-state))

(defonce ^:private current-ns (volatile! 'cljs.user))

(defonce ^:private app-opts (volatile! nil))

(def ^:private ^:const could-not-eval-regex #"Could not eval")
(def ^:private ^:const MACROS_SUFFIX "$macros")
(def ^:private ^:const JS_EXT ".js")
(def ^:private ^:const JSON_EXT ".json")

(def ^:private out-dir "target")

(def ^:private src-paths [out-dir])

(def fs (nodejs/require "fs"))
(def vm (nodejs/require "vm"))

;; =============================================================================
;; Analysis cache

(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(defn- cljs->transit-json
  [x]
  (let [wtr (transit/writer :json)]
    (transit/write wtr x)))

(defn- load-core-analysis-cache
  [ns-sym file-prefix]
  (let [keys        [:use-macros :excludes :name :imports :requires
                     :uses :defs :require-macros ::ana/constants :doc]
        cache (transit-json->cljs (js/LUMO_LOAD (str file-prefix JSON_EXT)))]
    (cljs/load-analysis-cache! st ns-sym cache)))

(defn- load-core-analysis-caches []
  (load-core-analysis-cache 'cljs.core "cljs/core.cljs.cache.aot")
  (load-core-analysis-cache 'cljs.core$macros "cljs/core$macros.cljc.cache"))

;; =============================================================================
;; Node.js filesystem API bridging

(defn- read-file
  "Accepts a filename to read and a callback. Upon success, invokes
  callback with the source. Otherwise invokes the callback with nil."
  [filename cb]
  (.readFile fs filename "utf-8"
    (fn [err source]
      (cb (when-not err
            source)))))

(defn- read-file-sync
  [filename]
  (try
    (.readFileSync fs filename "utf8")
    (catch :default _)))

(defn- write-file
  [filename data cb]
  (.writeFile fs filename data "utf-8" #(cb %)))

(defn- ensure-dir
  [path cb]
  (.stat fs path
    (fn [err stats]
      (cond
        err (.mkdir fs path #(cb))

        (not (.isDirectory stats))
        (throw (ex-info (str "Cache path `" path "` exists but is not a directory.") {}))

        :else (cb)))))

;; =============================================================================
;; Dependency loading

(defn- filename->lang
  "Converts a filename to a lang keyword by inspecting the file
  extension."
  [filename]
  (if (string/ends-with? filename JS_EXT)
    :js
    :clj))

(defn- replace-extension
  "Replaces the extension on a file."
  [filename new-extension]
  (string/replace filename #".clj[sc]?$" new-extension))

(defn- parse-edn
  "Parses edn source to Clojure data."
  [edn-source]
  (reader/read-string edn-source))

(defn- filenames-to-try
  "Produces a sequence of filenames to try reading, in the
  order they should be tried."
  [src-paths macros path]
  (let [extensions (if macros
                     [".clj" ".cljc"]
                     [".cljs" ".cljc" ".js"])]
    (for [extension extensions]
      (str path extension))))

(defn- load-goog
  "Loads a Google Closure implementation source file. `goog` namespaces are
   actually already included in the bundle because we compile with simple
   optimizations."
  [name cb]
  (cb {:source ""
       :lang   :js}))

(defn- skip-load-js?
  "Indicates namespaces for which JS code is already loaded, but for which
   we might need to load the corresponding analysis cache."
  [name macros]
  (and (not macros)
    ('#{cljs.analyzer
        cljs.analyzer.api
        cljs.compiler
        cljs.env
        cljs.js
        cljs.nodejs
        cljs.pprint
        cljs.reader
        cljs.source-map
        cljs.source-map.base64
        cljs.source-map.base64-vlq
        cljs.spec
        cljs.spec.impl.gen
        cljs.tagged-literals
        cljs.test
        cljs.tools.reader
        cljs.tools.reader.reader-types
        cljs.tools.reader.impl.commons
        cljs.tools.reader.impl.utils
        clojure.core.reducers
        clojure.data
        clojure.string
        clojure.set
        clojure.zip
        clojure.walk
        cognitect.transit} name)))

(defn- skip-load?
  [name macros?]
  ((if macros?
     '#{cljs.core
        cljs.js
        cljs.pprint
        cljs.env.macros
        cljs.analyzer.macros
        cljs.compiler.macros
        cljs.tools.reader.reader-types}
     '#{cljs.core
        com.cognitect.transit
        com.cognitect.transit.delimiters
        com.cognitect.transit.handlers
        com.cognitect.transit.util
        com.cognitect.transit.caching
        com.cognitect.transit.types
        com.cognitect.transit.eq
        com.cognitect.transit.impl.decoder
        com.cognitect.transit.impl.reader
        com.cognitect.transit.impl.writer})
   name))

(defn- load-bundled [path source cb]
  (when-let [cache-json (js/LUMO_LOAD (str path ".cache.json"))]
    (cb {:source source
         :lang :js
         :cache (transit-json->cljs cache-json)})
    :loaded))

;; TODO: can be optimized e.g. to just analyze CLJ source
;; if JS present but no analysis cache
(defn- load-external
  [path file-path macros? cb]
  ;; first check if the source is cached
  (let [cache-dir (:cache-path @app-opts)
        cache-prefix (str cache-dir "/" (munge path) (when macros? MACROS_SUFFIX))]
    (if-let [cached-source (and cache-dir
                                (js/LUMO_READ_FILE (str cache-prefix JS_EXT)))]
      (let [cached-analysis (js/LUMO_READ_FILE (str cache-prefix ".cache.json"))]
        (cb {:lang :js
             :source cached-source
             :filename (str cache-prefix JS_EXT)
             :cache cached-analysis})
        :loaded)
      (let [filename (str out-dir "/" file-path)]
        (when-let [source (read-file-sync filename)]
          (let [ret {:lang   (filename->lang filename)
                     :file   filename
                     :source source}]
            (if (or (string/ends-with? filename ".cljs")
                    (string/ends-with? filename ".cljc"))
              (if-let [javascript-source (read-file-sync (replace-extension filename JS_EXT))]
                (if-let [cache-edn (read-file-sync (str filename ".cache.edn"))]
                  (cb {:lang   :js
                       :source javascript-source
                       :cache  (parse-edn cache-edn)})
                  ;; one last attempt to read analysis cache
                  (if-let [cache-json (read-file-sync (str filename ".cache.json"))]
                    (cb {:lang   :js
                         :source javascript-source
                         :cache  (transit-json->cljs cache-json)})
                    (cb ret)))
                (cb ret))
              (cb ret)))
          :loaded)))))

(defn- load-and-cb!
  [name path file-path macros? cb]
  (let [bundled-src-prefix (cond-> path
                             macros? (str MACROS_SUFFIX))
        bundled-source (js/LUMO_LOAD (str bundled-src-prefix JS_EXT))
        skip-load-js? (skip-load-js? name macros?)]
    (cond
      skip-load-js?
      (load-bundled file-path "" cb)

      bundled-source
      ;; bundled source are AOTed macros which don't have the `.clj[sc]*` extension
      (load-bundled bundled-src-prefix bundled-source cb)

      :else
      (load-external path file-path macros? cb))))

(defn- load-other [{:keys [name path macros file]} cb]
  (loop [paths (filenames-to-try src-paths macros path)]
    (if-let [file-path (first paths)]
      (let [cb-res (load-and-cb! name path file-path macros cb)]
        (when-not cb-res
            (recur (next paths))))
      (cb nil))))

(defn- load [{:keys [name macros path file] :as m} cb]
  (cond
    (skip-load? name macros)
    (cb {:source ""
         :lang :js})

    (re-matches #"^goog/.*" path)
    (load-goog name cb)

    :else (load-other m cb)))

(defn- macros-cache? [cache]
  (.endsWith (str (:name cache)) MACROS_SUFFIX))

(defn- handle-caching-error
  [error]
  (throw (ex-info (str "Failed writing cache to " (.-path error))
           {:js-error error})))

(defn- write-cache
  [name path source cache prefix-path]
  (ensure-dir prefix-path
    (fn []
      (let [macros? (macros-cache? cache)
            filename-prefix (str prefix-path "/" (munge path) (when macros? MACROS_SUFFIX))
            cache-json (cljs->transit-json cache)
            cb #(when % (handle-caching-error %))]
        (write-file (str filename-prefix JS_EXT) source cb)
        (write-file (str filename-prefix ".cache.json") cache-json cb)))))

(defn- caching-node-eval
  "Evaluates JavaScript in node, writing source and analysis cache to disk
   when desired."
  [{:keys [name source cache path] :as m}]
  (when-let [cache-path (and source cache path (:cache-path @app-opts))]
    (write-cache name path source cache cache-path))
  (if-not js/COMPILED
    (.runInThisContext vm source (str (munge name) JS_EXT))
    (js/eval source)))

(defn- ^:boolean could-not-eval? [msg]
  (boolean (re-find could-not-eval-regex msg)))

(defn- handle-repl-error [error]
  (let [message (ex-message error)
        cause (ex-cause error)]
    (cond
      (could-not-eval? message)
      (let [message (ex-message cause)
            {:keys [column]} (ex-data cause)
            column-indicator-str (str (apply str
                                        (repeat (+ (count (name @current-ns)) 3 column) " "))
                                   "⬆")]
        (println column-indicator-str)
        (println message))
      (= message "ERROR")
      (println (str cause))

      :else
      (println error))))

(defn ^:export read-eval-print-str
  [source-str]
  (binding [cljs/*eval-fn* caching-node-eval
            cljs/*load-fn* load
            ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            env/*compiler* st
            r/resolve-symbol ana/resolve-symbol]
      (cljs/eval-str
        st
        source-str
        source-str
        {:ns            @current-ns
         :verbose       (:verbose @app-opts)
         :static-fns    false
         :context       :expr
         :def-emits-var true}
        (fn [{:keys [ns value error] :as ret}]
          (if-not error
            (do
              (println (pr-str value))
              (vreset! current-ns ns))
            (handle-repl-error error)))))
  nil)

(defn ^:export get-current-ns []
  @current-ns)

(defn ^:export init [verbose cache-path]
  (vreset! app-opts {:verbose verbose
                     :cache-path cache-path})
  (load-core-analysis-caches))
