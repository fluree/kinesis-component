(ns fluree.kinesis.stream
  (:require [donut.system :as ds]
            [fluree.db.util.json :as json]
            [fluree.db.util.log :as log])
  (:import (java.util.concurrent ExecutionException)
           (software.amazon.awssdk.core SdkBytes)
           (software.amazon.awssdk.services.kinesis KinesisAsyncClient)
           (software.amazon.awssdk.services.kinesis.model CreateStreamRequest
                                                          PutRecordRequest
                                                          ListStreamsRequest
                                                          ListStreamsResponse)))

(defn exists?
  [client stream-name]
  (log/debug "Checking if Kinesis stream" stream-name "exists")
  (loop [first-stream nil]
    (let [stream-req (if first-stream
                       (-> client
                           (.listStreams
                            (-> (ListStreamsRequest/builder)
                                (.exclusiveStartStreamName first-stream)
                                .build)))
                       (.listStreams client))
          stream-res ^ListStreamsResponse (.get stream-req)
          streams    (.streamNames stream-res)]
      (if ((set streams) stream-name)
        true
        (if (.hasMoreStreams stream-res)
          (recur (last streams))
          false)))))

(defn create!
  [client stream-name]
  (log/debug "Creating Kinesis stream" stream-name)
  (-> client
      (.createStream
       ^CreateStreamRequest
       (-> (CreateStreamRequest/builder)
           (.streamName stream-name)
           .build))
      .get))

(def component
  #::ds{:start
        (fn [{{:keys [kinesis/client]
               {:keys [kinesis/stream-name kinesis/create-stream?]} :aws/config}
              ::ds/config}]
          (log/info "Kinesis client:" client)
          (log/info "Kinesis stream:" stream-name)
          (when create-stream?
            (when-not (exists? client stream-name)
              (create! client stream-name)))
          stream-name)
        :config {:kinesis/client (ds/local-ref [:kinesis/client])
                 :aws/config     (ds/ref [:env :aws/config])}})

(defn publish-record
  "Given a kinesis stream name and some data, creates a PutRecordRequest bound for that stream that contains the provided data."
  [^KinesisAsyncClient kinesis-client stream-name ledger-name data]
  (log/debug "Publishing record to:" stream-name)
  (log/debug "Record data:" data)
  (let [^PutRecordRequest request (-> (PutRecordRequest/builder)
                                      (.partitionKey ledger-name)
                                      (.streamName stream-name)
                                      (.data (-> data
                                                 pr-str
                                                 SdkBytes/fromUtf8String))
                                      .build)]
    (try
      (let [result (.get (.putRecord kinesis-client request))]
        (log/debug "Kinesis record publish result:" result)
        result)
      (catch InterruptedException _
        (log/warn "Interrupted, assuming shutdown."))
      (catch ExecutionException e
        (log/error e "Exception while sending data to Kinesis. Will try again next cycle.")))))
