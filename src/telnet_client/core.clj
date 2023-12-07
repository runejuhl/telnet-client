(ns telnet-client.core
  (:gen-class)
  (:refer-clojure :exclude [read])
  (:import
    [org.apache.commons.net.telnet TelnetClient]
    [java.io OutputStream FileOutputStream]))

(declare disconnect)

(deftype Telnet [telnet read-future buf alive
                 hostname prompt isPrivileged]
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
                          :or   {port            23
                                 connect-timeout 5000
                                 default-timeout 5000
                                 output-log      nil}}]
   (let [#^TelnetClient tc (TelnetClient.)
         buf               (atom "")
         alive             (atom true)
         hostname          (atom nil)
         prompt            (atom nil)
         isPrivileged      (atom nil)]

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
                                 is   (.getInputStream tc)]
                             (while alive
                               (let [c (.read is tbuf)]
                                 (when (> c 0)
                                   (swap! buf str (String. tbuf 0 c)))
                                 (Thread/sleep 10))))
                           (catch Exception e e)))
              buf
              alive
              hostname
              prompt
              isPrivileged)))
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

(defn write
  "Send string to the host."
  [#^Telnet host #^String data]
  (let [#^TelnetClient tc (.telnet host)
        #^OutputStream os (-> tc .getOutputStream)]
    (.write os (.getBytes data))
    (.flush os)))

(defn read
  "Read n characters from input buffer. Read all characters if no n paramater."
  ([#^Telnet host n] (let [s @(.buf host) len (count s)]
                       (subs s 0 (if (> n len) len n))))
  ([#^Telnet host] (read host 2048)))
