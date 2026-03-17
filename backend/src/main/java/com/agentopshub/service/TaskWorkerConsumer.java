package com.agentopshub.service;

import com.agentopshub.dto.TaskDispatchMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.agent.dispatch.mode", havingValue = "mq")
public class TaskWorkerConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(TaskWorkerConsumer.class);

    private final TaskService taskService;
    private final TaskQueueProducer taskQueueProducer;
    private final String dispatchMode;

    public TaskWorkerConsumer(TaskService taskService,
                              TaskQueueProducer taskQueueProducer,
                              @Value("${app.agent.dispatch.mode:threadpool}") String dispatchMode) {
        this.taskService = taskService;
        this.taskQueueProducer = taskQueueProducer;
        this.dispatchMode = dispatchMode == null ? "threadpool" : dispatchMode.trim().toLowerCase();
    }

    @RabbitListener(queues = "${app.task.queue.main-queue:task.queue}", containerFactory = "taskListenerContainerFactory")
    public void onMessage(TaskDispatchMessage payload, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        if (!"mq".equals(dispatchMode)) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (payload == null || payload.getTaskId() == null || payload.getTaskId().isBlank()) {
            LOG.warn("Skip invalid task queue message: missing taskId");
            channel.basicAck(deliveryTag, false);
            return;
        }

        TaskService.QueueDispatchResult result = taskService.processQueuedTask(payload);
        switch (result.action()) {
            case ACK:
                channel.basicAck(deliveryTag, false);
                return;
            case RETRY:
                TaskDispatchMessage retryMessage = new TaskDispatchMessage(
                    payload.getTaskId(),
                    payload.getMessageId(),
                    result.retryCount(),
                    System.currentTimeMillis()
                );
                taskQueueProducer.publishRetry(retryMessage, result.retryDelayMs());
                channel.basicAck(deliveryTag, false);
                return;
            case DLQ:
                TaskDispatchMessage dlqMessage = new TaskDispatchMessage(
                    payload.getTaskId(),
                    payload.getMessageId(),
                    result.retryCount(),
                    System.currentTimeMillis()
                );
                taskQueueProducer.publishDlq(dlqMessage);
                channel.basicAck(deliveryTag, false);
                return;
            default:
                LOG.warn("Unexpected queue action for taskId={}, fallback ack", payload.getTaskId());
                channel.basicAck(deliveryTag, false);
        }
    }
}
