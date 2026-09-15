package com.teste.orders.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Order {

    private UUID id;

    @JsonProperty("produto")
    private String product;

    @JsonProperty("quantidade")
    private int quantity;

    @JsonProperty("dataCriacao")
    private LocalDateTime createdAt;

    public Order() {
    }

    public Order(UUID id, String product, int quantity, LocalDateTime createdAt) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}