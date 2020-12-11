# telnet_client
A Clojure library designed to operate on Huawei network devices.
For example routers & switchs.

## Usage
	(require '[telnet-client.huawei :refer [login exec-cmd]])
	(with-open [h (login "192.168.0.1" "username" "password")]
		(println (exec-cmd h "display version")))

## Authors
cdzwm

## License
MIT License
