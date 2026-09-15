package com.teste.orders.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.Channel;
import com.teste.orders.config.RabbitConfig;
import com.teste.orders.domain.Order;
import com.teste.orders.domain.OrderProcessingException;
import com.teste.orders.domain.OrderStatus;

@Service
public class OrderProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderProcessor.class);

    private final Map<UUID, OrderStatus> statusCache = new ConcurrentHashMap<>();
    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();

    public OrderProcessor(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public OrderStatus getStatus(UUID id) {
        return statusCache.get(id);
    }

    public void markAsReceived(UUID id) {
        statusCache.put(id, new OrderStatus(id, OrderStatus.RECEIVED));
    }

    @RabbitListener(queues = RabbitConfig.ENTRY_QUEUE)
    public void process(Order order,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException, InterruptedException {
        LOGGER.info("Processing order {} started", order.getId());
        try {
            Thread.sleep(1000 + random.nextInt(2000));
            if (random.nextDouble() < 0.2) {
                throw new OrderProcessingException("Problema temporario no processamento do pedido");
            }
            rabbitTemplate.convertAndSend(RabbitConfig.SUCCESS_STATUS_QUEUE,
                    new OrderStatus(order.getId(), OrderStatus.SUCCESS));
            updateStatus(order.getId(), OrderStatus.SUCCESS, null);
            channel.basicAck(deliveryTag, false);
            LOGGER.info("Processing order {} finished with success", order.getId());
        } catch (OrderProcessingException e) {
            rabbitTemplate.convertAndSend(RabbitConfig.FAILURE_STATUS_QUEUE,
                    buildFailureStatus(order.getId(), e.getMessage()));
            updateStatus(order.getId(), OrderStatus.FAILURE, e.getMessage());
            // Reject without requeue: the original message is routed to the DLQ
            channel.basicReject(deliveryTag, false);
            LOGGER.error("Processing order {} failed: {}", order.getId(), e.getMessage());
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("Unexpected failure while processing order", e);
        }
    }

    private void updateStatus(UUID id, String status, String error) {
        OrderStatus statusRecord = new OrderStatus(id, status);
        statusRecord.setProcessedAt(LocalDateTime.now());
        statusRecord.setErrorMessage(error);
        statusCache.put(id, statusRecord);
    }

    private OrderStatus buildFailureStatus(UUID id, String error) {
        OrderStatus status = new OrderStatus(id, OrderStatus.FAILURE);
        status.setErrorMessage(error);
        return status;
    }
}