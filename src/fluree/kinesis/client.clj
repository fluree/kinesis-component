(ns fluree.kinesis.client
  (:import (java.net URI)
           (software.amazon.awssdk.auth.credentials AwsBasicCredentials
                                                    ProfileCredentialsProvider)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.kinesis KinesisAsyncClient
                                                    KinesisAsyncClientBuilder)
           (software.amazon.kinesis.common KinesisClientUtil)))

(set! *warn-on-reflection* true)

(defn create
  [{:keys [aws/region aws/endpoint-override aws/profile] :as config}]
  (-> (KinesisAsyncClient/builder)
      (.region (Region/of region))
      (cond-> profile (.credentialsProvider (ProfileCredentialsProvider/create profile)))
      (.build)))
