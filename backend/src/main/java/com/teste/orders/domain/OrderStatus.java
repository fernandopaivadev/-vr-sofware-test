package com.teste.orders.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderStatus {

    public static final String RECEIVED = "RECEBIDO";
    public static final String PROCESSING = "PROCESSANDO";
    public static final String SUCCESS = "SUCESSO";
    public static final String FAILURE = "FALHA";

    @JsonProperty("idPedido")
    private UUID orderId;

    private String status;

    @JsonProperty("dataProcessamento")
    private LocalDateTime processedAt;

    @JsonProperty("mensagemErro")
    private String errorMessage;

    public OrderStatus() {
    }

    public OrderStatus(UUID orderId, String status) {
        this.orderId = orderId;
        this.status = status;
        this.processedAt = LocalDateTime.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}