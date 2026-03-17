package com.agentopshub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.task.queue")
public class TaskQueueProperties {
    private String exchange = "task.exchange";
    private String routingKey = "task.dispatch";
    private String retryRoutingKey = "task.retry";
    private String deadLetterExchange = "task.dlx";
    private String deadLetterRoutingKey = "task.dead";
    private String mainQueue = "task.queue";
    private String retryQueue = "task.queue.retry";
    private String deadLetterQueue = "task.dlq";
    private int maxRetries = 3;
    private int baseBackoffMs = 5000;
    private int backoffMultiplier = 2;
    private int consumerPrefetch = 1;
    private int consumerConcurrency = 2;

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getRoutingKey() { return routingKey; }
    public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
    public String getRetryRoutingKey() { return retryRoutingKey; }
    public void setRetryRoutingKey(String retryRoutingKey) { this.retryRoutingKey = retryRoutingKey; }
    public String getDeadLetterExchange() { return deadLetterExchange; }
    public void setDeadLetterExchange(String deadLetterExchange) { this.deadLetterExchange = deadLetterExchange; }
    public String getDeadLetterRoutingKey() { return deadLetterRoutingKey; }
    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) { this.deadLetterRoutingKey = deadLetterRoutingKey; }
    public String getMainQueue() { return mainQueue; }
    public void setMainQueue(String mainQueue) { this.mainQueue = mainQueue; }
    public String getRetryQueue() { return retryQueue; }
    public void setRetryQueue(String retryQueue) { this.retryQueue = retryQueue; }
    public String getDeadLetterQueue() { return deadLetterQueue; }
    public void setDeadLetterQueue(String deadLetterQueue) { this.deadLetterQueue = deadLetterQueue; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = Math.max(0, maxRetries); }
    public int getBaseBackoffMs() { return baseBackoffMs; }
    public void setBaseBackoffMs(int baseBackoffMs) { this.baseBackoffMs = Math.max(1000, baseBackoffMs); }
    public int getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(int backoffMultiplier) { this.backoffMultiplier = Math.max(1, backoffMultiplier); }
    public int getConsumerPrefetch() { return consumerPrefetch; }
    public void setConsumerPrefetch(int consumerPrefetch) { this.consumerPrefetch = Math.max(1, consumerPrefetch); }
    public int getConsumerConcurrency() { return consumerConcurrency; }
    public void setConsumerConcurrency(int consumerConcurrency) { this.consumerConcurrency = Math.max(1, consumerConcurrency); }
}
