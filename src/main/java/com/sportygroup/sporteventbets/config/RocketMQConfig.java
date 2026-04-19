package com.sportygroup.sporteventbets.config;

import com.sportygroup.sporteventbets.rocketmq.BetSettlementConsumer;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

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
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder()
                .setEndpoints(nameServer)
                .build();
        return provider.newProducerBuilder()
                // Set the number of retries for producer reliability
                .setMaxAttempts(3)
                .setClientConfiguration(clientConfiguration)
                .build();
    }

    @Bean(destroyMethod = "close")
    public PushConsumer rocketMQConsumer(ClientServiceProvider provider, BetSettlementConsumer betSettlementConsumer) throws Exception {
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder()
                .setEndpoints(nameServer)
                .build();
        return provider.newPushConsumerBuilder()
                .setClientConfiguration(clientConfiguration)
                .setConsumerGroup(consumerGroup)
                .setSubscriptionExpressions(Collections.singletonMap("bet-settlements", null))
                .setMessageListener(betSettlementConsumer)
                .build();
    }
}