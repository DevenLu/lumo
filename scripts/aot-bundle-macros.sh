set -e

# remove the target dir
rm -rf target

# move the backed up resources back to target
mv resources_bak target

echo "### Compiling Macro Namespaces"

mkdir -p lumo-cljs/out/macros-tmp

$(pwd)/main --quiet -dk lumo-cljs/out/macros-tmp <<REPL_INPUT
(require-macros 'clojure.template 'cljs.spec 'cljs.spec.impl.gen 'cljs.test)
REPL_INPUT

sleep 1

mv lumo-cljs/out/macros-tmp/clojure_SLASH_template\$macros.js target/clojure/template\$macros.js
mv lumo-cljs/out/macros-tmp/clojure_SLASH_template\$macros.cache.json target/clojure/template\$macros.cache.json
mv lumo-cljs/out/macros-tmp/cljs_SLASH_test\$macros.js target/cljs/test\$macros.js
mv lumo-cljs/out/macros-tmp/cljs_SLASH_test\$macros.cache.json target/cljs/test\$macros.cache.json
mv lumo-cljs/out/macros-tmp/cljs_SLASH_spec\$macros.js target/cljs/spec\$macros.js
mv lumo-cljs/out/macros-tmp/cljs_SLASH_spec\$macros.cache.json target/cljs/spec\$macros.cache.json
mv lumo-cljs/out/macros-tmp/cljs_SLASH_spec_SLASH_impl_SLASH_gen\$macros.js target/cljs/spec/impl/gen\$macros.js
mv lumo-cljs/out/macros-tmp/cljs_SLASH_spec_SLASH_impl_SLASH_gen\$macros.cache.json target/cljs/spec/impl/gen\$macros.cache.json

rm -rf lumo-cljs
