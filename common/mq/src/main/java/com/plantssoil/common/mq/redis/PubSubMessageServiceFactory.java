package com.plantssoil.common.mq.redis;

import java.time.Duration;

import org.apache.commons.configuration.Configuration;

import com.plantssoil.common.config.ConfigFactory;
import com.plantssoil.common.config.LettuceConfiguration;
import com.plantssoil.common.mq.IMessageConsumer;
import com.plantssoil.common.mq.IMessagePublisher;
import com.plantssoil.common.mq.IMessageServiceFactory;
import com.plantssoil.common.mq.exception.MessageQueueException;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * The IMessageServiceFactory implementation base on Redis PubSub.<br/>
 * Referenced Redis Lettuce documentation, redis-lettuce does't need connection
 * pool for non-blocking commands.<br/>
 * There are only 2 connections in this factory, one for publishing and another
 * for subscribing.<br/>
 * 
 * @author danialdy
 * @Date 6 Nov 2024 3:44:21 pm
 */
public class PubSubMessageServiceFactory implements IMessageServiceFactory {
    private RedisClient client;
    private long connectionTimeout = 30 * 1000;
    private StatefulRedisPubSubConnection<String, String> publisherConnection;
    private StatefulRedisPubSubConnection<String, String> consumerConnection;
    private PubSubMessageDispatcher messageDispatcher;

    /**
     * Constructor<br/>
     * Initialize connection pools for publisher and consumer<br/>
     */
    public PubSubMessageServiceFactory() {
        Configuration configuration = ConfigFactory.getInstance().getConfiguration();

        // initiate the RedisClient
        if (configuration.containsKey(LettuceConfiguration.MESSAGE_SERVICE_URI)) {
            // initialize consumer & publisher factory
            String uri = ConfigFactory.getInstance().getConfiguration().getString(LettuceConfiguration.MESSAGE_SERVICE_URI);
            this.client = RedisClient.create(uri);
        } else {
            throw new MessageQueueException(MessageQueueException.BUSINESS_EXCEPTION_CODE_15004,
                    String.format("Don't find configuration '%s'!", LettuceConfiguration.MESSAGE_SERVICE_URI));
        }
        // Sets the connection timeout value for getting Connections and following
        // commands in milliseconds, defaults to 30 seconds.
        if (configuration.containsKey(LettuceConfiguration.MESSAGE_SERVICE_CONNECTION_TIMEOUT)) {
            this.connectionTimeout = configuration.getLong(LettuceConfiguration.MESSAGE_SERVICE_CONNECTION_TIMEOUT);
        }

        // create publisher connection
        this.publisherConnection = this.client.connectPubSub();
        this.publisherConnection.setTimeout(Duration.ofMillis(this.connectionTimeout));

        // create consumer connection
        this.consumerConnection = this.client.connectPubSub();
        this.consumerConnection.setTimeout(Duration.ofMillis(this.connectionTimeout));

        // add message receiver (dispatcher) on the consumer connection
        this.messageDispatcher = new PubSubMessageDispatcher();
        this.consumerConnection.addListener(this.messageDispatcher);
    }

    @Override
    public void close() throws Exception {
        if (this.publisherConnection != null) {
            this.publisherConnection.close();
        }
        if (this.consumerConnection != null) {
            this.consumerConnection.close();
        }
    }

    @Override
    public IMessagePublisher createMessagePublisher() {
        return new PubSubMessagePublisher(this.publisherConnection.async());
    }

    @Override
    public IMessageConsumer createMessageConsumer() {
        return new PubSubMessageConsumer(this.consumerConnection.async(), this.messageDispatcher);
    }

}
