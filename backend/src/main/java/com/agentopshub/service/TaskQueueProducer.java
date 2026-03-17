package com.agentopshub.service;

import com.agentopshub.config.TaskQueueProperties;
import com.agentopshub.dto.TaskDispatchMessage;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.agent.dispatch.mode", havingValue = "mq")
public class TaskQueueProducer {
    private final RabbitTemplate rabbitTemplate;
    private final TaskQueueProperties queueProperties;

    public TaskQueueProducer(RabbitTemplate rabbitTemplate, TaskQueueProperties queueProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueProperties = queueProperties;
    }

    public void publish(TaskDispatchMessage message) {
        rabbitTemplate.convertAndSend(queueProperties.getExchange(), queueProperties.getRoutingKey(), message);
    }

    public void publishRetry(TaskDispatchMessage message, long delayMs) {
        MessagePostProcessor ttlProcessor = rabbitMessage -> {
            rabbitMessage.getMessageProperties().setExpiration(String.valueOf(Math.max(1000, delayMs)));
            return rabbitMessage;
        };
        rabbitTemplate.convertAndSend(queueProperties.getExchange(), queueProperties.getRetryRoutingKey(), message, ttlProcessor);
    }

    public void publishDlq(TaskDispatchMessage message) {
        rabbitTemplate.convertAndSend(queueProperties.getDeadLetterExchange(), queueProperties.getDeadLetterRoutingKey(), message);
    }
}
