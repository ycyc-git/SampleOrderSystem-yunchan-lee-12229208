package org.example.domain;

import java.time.LocalDateTime;

public class ProductionJob {

    private final String jobId;
    private final Order order;
    private final int shortage;
    private final int actualProductionQty;
    private final double totalProductionTime;
    private LocalDateTime startedAt;

    public ProductionJob(String jobId, Order order, int shortage,
                         int actualProductionQty, double totalProductionTime,
                         LocalDateTime startedAt) {
        this.jobId = jobId;
        this.order = order;
        this.shortage = shortage;
        this.actualProductionQty = actualProductionQty;
        this.totalProductionTime = totalProductionTime;
        this.startedAt = startedAt;
    }

    public String getJobId()                  { return jobId; }
    public Order getOrder()                   { return order; }
    public int getShortage()                  { return shortage; }
    public int getActualProductionQty()       { return actualProductionQty; }
    public double getTotalProductionTime()    { return totalProductionTime; }
    public LocalDateTime getStartedAt()       { return startedAt; }
    public void setStartedAt(LocalDateTime t) { this.startedAt = t; }
}
