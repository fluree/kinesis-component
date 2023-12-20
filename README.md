# com.fluree/kinesis-component

Fluree Kinesis [donut.system](https://github.com/donut-party/system) component

## Usage

```clojure
; in deps.edn
com.fluree/kinesis-component {:git/url "https://github.com/fluree/kinesis-component.git"
                              :git/sha "..."}

; in your code
(ns my.ns
  (:require [donut.system :as ds]
            [fluree.kinesis :as kinesis]))

(def my-donut-system
  {::ds/defs
   {:aws        {:kinesis/client kinesis/client
                 :kinesis/stream kinesis/stream}
    :aws/config {:aws/region "us-west-2" ; required
                 :aws/endpoint-override "http://localhost:4566" ; optional
                 :aws/access-key-id "..." ; optional & not recommended for real credentials
                 :kinesis/stream-name "foo" ; required
                 :kinesis/create-stream? true ; optional; defaults to false
                 }}})
```

## License

Copyright © 2023 Fluree, PBC

See LICENSE file.
