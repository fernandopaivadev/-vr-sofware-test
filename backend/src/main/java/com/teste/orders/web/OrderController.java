package com.teste.orders.web;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teste.orders.config.RabbitConfig;
import com.teste.orders.domain.Order;
import com.teste.orders.domain.OrderStatus;
import com.teste.orders.service.OrderProcessor;

@RestController
@RequestMapping("/api/pedidos")
public class OrderController {

    private final RabbitTemplate rabbitTemplate;
    private final OrderProcessor orderProcessor;

    public OrderController(RabbitTemplate rabbitTemplate, OrderProcessor orderProcessor) {
        this.rabbitTemplate = rabbitTemplate;
        this.orderProcessor = orderProcessor;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Order order) {
        Map<String, String> errors = validate(order);
        if (!errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
        }
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(LocalDateTime.now());
        }
        rabbitTemplate.convertAndSend(RabbitConfig.ENTRY_QUEUE, order);
        orderProcessor.markAsReceived(order.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(order);
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<OrderStatus> getStatus(@PathVariable UUID id) {
        OrderStatus status = orderProcessor.getStatus(id);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    private Map<String, String> validate(Order order) {
        Map<String, String> errors = new HashMap<>();
        if (order.getId() == null) {
            errors.put("id", "id e obrigatorio");
        }
        if (order.getProduct() == null || order.getProduct().trim().isEmpty()) {
            errors.put("produto", "produto nao pode ser vazio");
        }
        if (order.getQuantity() <= 0) {
            errors.put("quantidade", "quantidade deve ser maior que zero");
        }
        return errors;
    }
}