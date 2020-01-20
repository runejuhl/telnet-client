(ns telnet-client.huawei
  (:refer-clojure :exclude [read])
  (:import [telnet_client.core Telnet])
  (:require
    [telnet-client.core :refer (read wait write get-telnet)]
    [clojure.string :as cs :refer [join split-lines]])
  (:gen-class))

(defn- tail-cmd-prompt
  "command prompt regex"
  []
  #"(?m)^(?:<([^<>]+)>|\[([^\[\]]+)\])$")

(defn- cmd-prompt
  "command prompt regex"
  []
  #"(?m)^(?:<([^<>]+)>|\[([^\[\]]+)\])")

(defn wait-cmd-prompt
  "Wait until a command line prompt is found."
  ([#^Telnet host timeout]
   (let [buf @(.buf host)]
     (wait 10 timeout re-find (tail-cmd-prompt) buf)))
  ([#^Telnet host]
   (wait-cmd-prompt host 2000)))

(defn- exec-ansi-shift-right-cmds
  "Return a string with ANSI characters removed."
  [s]
  (let [m (re-matcher  #"\u001B\[(\d+)(D)" s)]
    (loop [result "" last-offset 0]
      (if (re-find m)
        (let [[start end n cmd-name] [(long (.start m)) (long (.end m)) (.group m 1) (.group m 2)]
              line (str result (subs s last-offset start))
              cnt (- (count line) (Integer/parseInt n))
              len (if (< cnt 0) 0 cnt)]
          (recur (subs line 0 len) (long end)))
        (str result (subs s last-offset))))))

(defn wait-for
  "Waitting until a regex is found in the input buffer."
  ([#^Telnet host re timeout]
   (let [input (wait 10 timeout
                             #(let [m (re-matcher re @(.buf host))]
                                (when (re-find m)
                                  (let [result (subs @(.buf host) 0 (.end m))]
                                    (swap! (.buf host) subs (.end m))
                                    result))))]
     (if input (join "\n" (map exec-ansi-shift-right-cmds (split-lines input))))))
  ([#^Telnet host re]
   (wait-for host re 2000)))

(defn exec-cmd
  "Execute a command, and return the result as string.
  Clear buffer before executing the command."
  ([#^Telnet host cmd cr]
   (swap! (.buf host) (constantly ""))
   (write host (str cmd (if cr "\n" "")))
   (let [buf (.buf host) prompt (cmd-prompt)]
     (let [input (loop [result ""]
                   (let [d @buf
                         m-more (re-matcher #"(?m)^.*?---- More ----.*?$" d )
                         m-prompt (re-matcher prompt d)]
                     (if (re-find m-prompt)
                       (do (swap! buf subs (.end m-prompt))
                           (str result (subs d 0 (.end m-prompt))))
                       (do (if (re-find m-more)
                             (do (swap! buf subs (.end m-more))
                                 (write host " ")
                                 (Thread/sleep 100)
                                 (recur (str result (subs d 0 (.end m-more)))))
                             (do (Thread/sleep 100)
                                 (recur result)))))))]
       (join "\n" (map exec-ansi-shift-right-cmds (cs/split-lines input))))))
  ([#^Telnet host cmd]
   (exec-cmd host cmd true)))

(defn login
  "Login to the host specified by host with username and password."
  ([host user password timeout]
   (let [#^Telnet telnet (get-telnet host)
         ts 200
         start (. System currentTimeMillis)]
     (loop [data @(.buf telnet)]
       (when (< (- (. System currentTimeMillis) start) timeout)
         (cond
           (wait-for telnet #"Username:" ts) (do  (write telnet (str user "\n")) (recur @(.buf telnet)))
           (wait-for telnet #"Password:" ts) (do  (write telnet (str password "\n")) (recur @(.buf telnet)))
           (wait-cmd-prompt telnet ts) telnet
           :default (do (Thread/sleep 50) (recur @(.buf telnet))))))))
  ([host user password]
   (login host user password 2000)))

(defn get-host-name
  "Get the name of the host."
  [host]
  (let [result (exec-cmd host "\n")]
    (apply str (rest (re-find (tail-cmd-prompt) result)))))

(defn has-cmd
  [host cmd]
  (let [ret (exec-cmd host (str cmd " ?" false))]
    (write host "Clear current command.\n")
    (wait-for host (tail-cmd-prompt))
    (if-not (re-find #"(?m)^\s*% Unrecognized command" ret) true)))
