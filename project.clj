(defproject telnet-client "0.1.10"
  :description "Clojure wrap for TelnetClient"
  :url "https://github.com/cdzwm/telnet-client"
  :license {:name "MIT License"
            :url "https://www.mit-license.org/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [commons-net/commons-net "3.6"]]
  :profiles {
             :dev {:plugins [[cider/cider-nrepl "0.22.4"]]
                   :repl-options {:init-ns telnet-client.core}}}
  :main telnet-client.core)
