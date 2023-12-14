(ns fluree.kinesis.client
  (:require [donut.system :as ds]
            [fluree.db.util.log :as log])
  (:import (java.net URI)
           (software.amazon.awssdk.auth.credentials AwsBasicCredentials
                                                    StaticCredentialsProvider)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.kinesis KinesisAsyncClient
                                                    KinesisAsyncClientBuilder)
           (software.amazon.kinesis.common KinesisClientUtil)))

(def component
  #::ds{:start
        (fn [{{:keys [aws/region kinesis/endpoint-override aws/access-key-id]
               :as   config} ::ds/config}]
          (log/debug "Kinesis config" config)
          (let [builder   (.region (KinesisAsyncClient/builder)
                                   (Region/of region))
                builder*  (if endpoint-override
                            (.endpointOverride builder
                                               (URI/create endpoint-override))
                            builder)
                builder** (if access-key-id
                            (.credentialsProvider
                             ^KinesisAsyncClientBuilder builder*
                             ^StaticCredentialsProvider
                             (StaticCredentialsProvider/create
                              ;; This is only used for dev / test credentials w/ localstack
                              (AwsBasicCredentials/create access-key-id "ignored")))
                            builder*)]
            (KinesisClientUtil/createKinesisAsyncClient builder**)))
        :config (ds/ref [:env :aws/config])})
