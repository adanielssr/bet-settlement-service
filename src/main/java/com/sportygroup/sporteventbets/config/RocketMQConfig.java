package com.sportygroup.sporteventbets.config;

import com.sportygroup.sporteventbets.rocketmq.BetSettlementConsumer;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

import static org.apache.rocketmq.client.apis.consumer.FilterExpression.SUB_ALL;

@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Bean
    public ClientServiceProvider clientServiceProvider() {
        return ClientServiceProvider.loadService();
    }

    @Bean(destroyMethod = "close")
    public Producer rocketMQProducer(ClientServiceProvider provider) throws Exception {
        // disabled SSL to match the default server configuration
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder()
                .setEndpoints(nameServer)
                .enableSsl(false)
                .build();
        return provider.newProducerBuilder()
                // Set the number of retries for producer reliability
                .setMaxAttempts(3)
                .setClientConfiguration(clientConfiguration)
                .build();
    }

    @Bean(destroyMethod = "close")
    public PushConsumer rocketMQConsumer(ClientServiceProvider provider, BetSettlementConsumer betSettlementConsumer) throws Exception {
        // disabled SSL to match the default server configuration
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder()
                .setEndpoints(nameServer)
                .enableSsl(false)
                .build();
        return provider.newPushConsumerBuilder()
                .setClientConfiguration(clientConfiguration)
                .setConsumerGroup(consumerGroup)
                .setSubscriptionExpressions(Collections.singletonMap("bet-settlements", SUB_ALL))
                .setMessageListener(betSettlementConsumer)
                .build();
    }
}