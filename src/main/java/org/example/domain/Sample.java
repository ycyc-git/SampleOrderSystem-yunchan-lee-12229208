package org.example.domain;

public class Sample {

    private String id;
    private String name;
    private double avgProductionTime;
    private double yield;
    private int stock;

    // Gson deserialization
    private Sample() {}

    public Sample(String id, String name, double avgProductionTime, double yield, int stock) {
        this.id = id;
        this.name = name;
        this.avgProductionTime = avgProductionTime;
        this.yield = yield;
        this.stock = stock;
    }

    public String getId()                { return id; }
    public String getName()              { return name; }
    public double getAvgProductionTime() { return avgProductionTime; }
    public double getYield()             { return yield; }
    public int getStock()                { return stock; }
    public void setStock(int stock)      { this.stock = stock; }
}
