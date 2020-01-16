# telnet_client
A Clojure library designed to operate on Huawei network devices.
For example routers & switchs.

## Usage
(require '[telnet-client.core :refer [login exec-cmd]])
(with-open [h (login "192.168.0.1" "username" "password")]
	(println (exec-cmd "display version\n")))

Authors
	cdzwm

## License
MIT License
