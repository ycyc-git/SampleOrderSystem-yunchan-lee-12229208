package org.example.domain;

import java.time.LocalDateTime;

public class Order {

    private final String orderId;
    private final Sample sample;
    private final String customerName;
    private final int quantity;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime releasedAt;

    public Order(String orderId, Sample sample, String customerName, int quantity,
                 OrderStatus status, LocalDateTime createdAt, LocalDateTime releasedAt) {
        this.orderId = orderId;
        this.sample = sample;
        this.customerName = customerName;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.releasedAt = releasedAt;
    }

    public void transition(OrderStatus newStatus) {
        if (!isValidTransition(newStatus)) {
            throw new IllegalStateException(
                    "허용되지 않는 상태 전이: " + this.status + " → " + newStatus);
        }
        this.status = newStatus;
    }

    private boolean isValidTransition(OrderStatus to) {
        switch (status) {
            case RESERVED:
                return to == OrderStatus.REJECTED
                    || to == OrderStatus.PRODUCING
                    || to == OrderStatus.CONFIRMED;
            case PRODUCING:
                return to == OrderStatus.CONFIRMED;
            case CONFIRMED:
                return to == OrderStatus.RELEASE;
            default:
                return false;
        }
    }

    public String getOrderId()           { return orderId; }
    public Sample getSample()            { return sample; }
    public String getCustomerName()      { return customerName; }
    public int getQuantity()             { return quantity; }
    public OrderStatus getStatus()       { return status; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime dt) { this.releasedAt = dt; }
}
