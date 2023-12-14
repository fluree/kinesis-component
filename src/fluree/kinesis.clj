(ns fluree.kinesis
  (:require [fluree.kinesis.client]
            [fluree.kinesis.stream]))

(def client fluree.kinesis.client/component)
(def stream fluree.kinesis.stream/component)
(def publish-record fluree.kinesis.stream/publish-record)
