(defproject telnet-client "0.1.23-clj1.9"
  :description "Clojure wrap for TelnetClient"
  :url "https://github.com/cdzwm/telnet-client"
  :license {:name "MIT License"
            :url "https://www.mit-license.org/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [commons-net/commons-net "3.6"]]
  :aot [telnet-client.core]
  :profiles {
             :dev {:plugins [[cider/cider-nrepl "0.22.4"]]
                   :repl-options {:init-ns telnet-client.core}}})
