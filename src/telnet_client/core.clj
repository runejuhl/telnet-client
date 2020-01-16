(ns telnet-client.core
  (:import
    [org.apache.commons.net.telnet TelnetClient]
    [java.io OutputStream FileOutputStream])
  (:require
    [clojure.string :as cs :refer [join split-lines]])
  (:gen-class))

(declare disconnect)

(deftype Telnet [telnet read-future buf alive]
  java.io.Closeable
  (close [this] (disconnect this)))

(defn wait
  "polls to see if f returns true with interval (milliseconds)
  and timeout (milliseconds). can pass additional args to f."
  [interval timeout f & args]
  (let [start (. System currentTimeMillis)]
    (loop [current-time (. System currentTimeMillis)]
      (if (< (- current-time start) timeout)
        (if-let [result (apply f args)]
          result
          (do (. Thread sleep interval)
              (recur (. System currentTimeMillis))))))))

(defn get-telnet
  "returns a telnetclient given server-ip as String.
  Support options:
  :port (default 23)
  :connet-timeout (default 5000)
  :default-timeout (default 5000)
  :output-log (default nil)
  Add method close so that the object can be used with with-open."
  ([#^String server-ip & {:keys [port connect-timeout default-timeout output-log]
                         :or {port 23
                              connect-timeout 5000
                              default-timeout 5000
                              output-log nil}}]
   (let [#^TelnetClient tc (TelnetClient.)
         buf (atom "")
         alive (atom true)]

     (when output-log
       (.registerSpyStream tc (FileOutputStream. "output.log")))

     (doto tc
       (.setConnectTimeout connect-timeout)
       (.setDefaultTimeout default-timeout)
       (.setReaderThread true)
       (.connect server-ip #^long port)
       (.setKeepAlive true))

     (Telnet. tc
              (future (try (let [tbuf (byte-array 2048)
                                 is (.getInputStream tc)]
                             (while alive
                               (let [c (.read is tbuf)]
                                 (when (> c 0)
                                   (swap! buf str (String. tbuf 0 c)))
                                 (Thread/sleep 10))))
                           (catch Exception e e)))
              buf
              alive)))
  ([#^String server-ip]
   (get-telnet server-ip :port 23)))

(defn disconnect
  "disconnects telnet-client"
  [#^Telnet telnet-client]
  (when-let [#^TelnetClient tc (.telnet telnet-client)]
    (.isConnected tc)
    (.stopSpyStream tc)
    (.disconnect tc)
    (swap! (.alive telnet-client) (constantly false))
    @(.read-future telnet-client)))

(defn cmd-prompt
  "command prompt regex"
  []
  #"(?m)^(?:<([^<>]+)>|\[([^\[\]]+)\])$")

(defn wait-cmd-prompt
  "Wait until a command line prompt is found."
  ([#^Telnet host timeout]
   (let [buf @(.buf host)]
     (wait 10 timeout re-find (cmd-prompt) buf)))
  ([#^Telnet host]
   (wait-cmd-prompt host 2000)))

(defn exec-ansi-shift-right-cmds
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

(defn write
  "Send string to the host."
  [#^Telnet host #^String data]
  (let [#^TelnetClient tc (.telnet host)
        #^OutputStream os (-> tc .getOutputStream)]
    (.write os (.getBytes data))
    (.flush os)))

(defn read-data
  "Read n characters from input buffer. Read all characters if no n paramater."
  ([#^Telnet host n] (subs @(.buf host) 0 n))
  ([#^Telnet host] (read-data host 2048)))

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
  [#^Telnet host cmd]
  (swap! (.buf host) (constantly ""))
  (write host (str cmd "\n"))
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
    (apply str (rest (re-find (cmd-prompt) result)))))
