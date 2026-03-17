package com.agentopshub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.agent.dispatch.mode", havingValue = "mq")
@EnableConfigurationProperties(TaskQueueProperties.class)
public class RabbitMqConfig {

    @Bean
    public DirectExchange taskExchange(TaskQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange taskDeadLetterExchange(TaskQueueProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue taskQueue(TaskQueueProperties properties) {
        return QueueBuilder.durable(properties.getMainQueue()).build();
    }

    @Bean
    public Queue taskRetryQueue(TaskQueueProperties properties) {
        return QueueBuilder.durable(properties.getRetryQueue())
            .withArguments(Map.of(
                "x-dead-letter-exchange", properties.getExchange(),
                "x-dead-letter-routing-key", properties.getRoutingKey()
            ))
            .build();
    }

    @Bean
    public Queue taskDeadLetterQueue(TaskQueueProperties properties) {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding taskQueueBinding(Queue taskQueue, DirectExchange taskExchange, TaskQueueProperties properties) {
        return BindingBuilder.bind(taskQueue).to(taskExchange).with(properties.getRoutingKey());
    }

    @Bean
    public Binding taskRetryQueueBinding(Queue taskRetryQueue, DirectExchange taskExchange, TaskQueueProperties properties) {
        return BindingBuilder.bind(taskRetryQueue).to(taskExchange).with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding taskDeadLetterQueueBinding(Queue taskDeadLetterQueue,
                                              DirectExchange taskDeadLetterExchange,
                                              TaskQueueProperties properties) {
        return BindingBuilder.bind(taskDeadLetterQueue).to(taskDeadLetterExchange).with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public MessageConverter taskMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter taskMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(taskMessageConverter);
        return template;
    }

    @Bean(name = "taskListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory taskListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                             MessageConverter taskMessageConverter,
                                                                             TaskQueueProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(taskMessageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(properties.getConsumerConcurrency());
        factory.setPrefetchCount(properties.getConsumerPrefetch());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
