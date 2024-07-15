(ns fluree.kinesis.stream
  (:require [fluree.db.util.log :as log])
  (:refer-clojure :exclude [list])
  (:import (java.util.concurrent CompletableFuture ExecutionException)
           (software.amazon.awssdk.core SdkBytes)
           (software.amazon.awssdk.services.kinesis KinesisAsyncClient)
           (software.amazon.awssdk.services.kinesis.model
            CreateStreamRequest DescribeStreamSummaryRequest
            StreamDescriptionSummary StreamStatus DescribeStreamSummaryResponse
            PutRecordRequest ListStreamsRequest ListStreamsResponse
            StreamSummary)))

(set! *warn-on-reflection* true)

(defmulti dejavaify-stream-summary (fn [pojo] (class pojo)))

(defmethod dejavaify-stream-summary StreamSummary
  [^StreamSummary summary]
  {:name   (.streamName summary)
   :arn    (.streamARN summary)
   :status (.streamStatus summary)})

(defmethod dejavaify-stream-summary StreamDescriptionSummary
  [^StreamDescriptionSummary summary]
  {:name   (.streamName summary)
   :arn    (.streamARN summary)
   :status (.streamStatus summary)})

(defn list
  [^KinesisAsyncClient client & [short-circuit-name]]
  (loop [first-stream nil
         streams      []]
    (let [stream-req   (if first-stream
                         (-> client
                             (.listStreams
                              ^ListStreamsRequest
                              (-> (ListStreamsRequest/builder)
                                  (.exclusiveStartStreamName first-stream)
                                  .build)))
                         (.listStreams client))
          stream-res   ^ListStreamsResponse (.get ^CompletableFuture stream-req)
          _            (log/debug "Received stream list response:"
                                  (str stream-res))
          summaries    (->> stream-res .streamSummaries
                            (map dejavaify-stream-summary))
          next-streams (concat streams summaries)]
      (if-let [summary (and short-circuit-name
                            (some #(when (= short-circuit-name (:name %)) %)
                                  summaries))]
        [summary]
        (if (.hasMoreStreams stream-res)
          (recur (-> summaries last :name) next-streams)
          next-streams)))))

(defn summary
  [client stream-name]
  (let [streams (list client stream-name)
        fstream (first streams)]
    (when (and (= 1 (count streams))
               (= stream-name (:name fstream)))
      fstream)))

(defn exists?
  [client stream-name]
  (log/debug "Checking if Kinesis stream" stream-name "exists")
  (->> stream-name (summary client) boolean))

(defn create!
  [^KinesisAsyncClient client stream-name]
  (log/debug "Creating Kinesis stream" stream-name)
  (let [stream (-> client
                   ^CompletableFuture
                   (.createStream
                    ^CreateStreamRequest
                    (-> (CreateStreamRequest/builder)
                        (.streamName stream-name)
                        .build))
                   .get)]
    (log/debug "Waiting for Kinesis stream" stream-name "to be ACTIVE")
    (loop []
      (let [summary-resp (-> client
                             ^CompletableFuture
                             (.describeStreamSummary
                              ^DescribeStreamSummaryRequest
                              (-> (DescribeStreamSummaryRequest/builder)
                                  (.streamName stream-name)
                                  .build))
                             .get)
            {:keys [status] :as summary} (-> ^DescribeStreamSummaryResponse
                                             summary-resp
                                             .streamDescriptionSummary
                                             dejavaify-stream-summary)]
        (if (= status StreamStatus/ACTIVE)
          summary
          (do
            (Thread/sleep 500)
            (recur)))))))

(defn start
  [{:keys                                                [aws/kinesis-client]
    {:keys [kinesis/stream-name kinesis/create-stream?]} :aws/config}]
  (log/info "Kinesis stream:" stream-name)
  (let [stream-exists? (exists? kinesis-client stream-name)
        stream         (if stream-exists?
                         (summary kinesis-client stream-name)
                         (if create-stream?
                           (do
                             (log/info "Kinesis stream doesn't exist, creating it")
                             (create! kinesis-client stream-name))
                           (throw (IllegalArgumentException.
                                   (str "Kinesis stream " stream-name
                                        " does not exist")))))]
    (log/info "Kinesis stream ARN:" (:arn stream))
    {:stream stream, :client kinesis-client}))

(defn publish-record
  "Given a kinesis stream name and some data, creates a PutRecordRequest bound for that stream that contains the provided data."
  [^KinesisAsyncClient kinesis-client stream-name ledger-name data]
  (log/debug "Publishing record to:" stream-name)
  (log/debug "Record data:" data)
  (let [^PutRecordRequest request (-> (PutRecordRequest/builder)
                                      (.partitionKey ledger-name)
                                      (.streamName stream-name)
                                      (.data (-> data
                                                 json/stringify
                                                 SdkBytes/fromUtf8String))
                                      .build)]
    (try
      (let [result (.get (.putRecord kinesis-client request))]
        (log/debug "Kinesis record publish result:" (.toString result))
        result)
      (catch InterruptedException _
        (log/warn "Interrupted, assuming shutdown."))
      (catch ExecutionException e
        (log/error e "Exception while sending data to Kinesis. Will try again next cycle.")))))
