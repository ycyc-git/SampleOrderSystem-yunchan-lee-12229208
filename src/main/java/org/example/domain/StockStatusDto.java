package org.example.domain;

public class StockStatusDto {

    private final Sample sample;
    private final int stock;
    private final int pendingDemand;
    private final String stockLabel;
    private final int remainingRate;

    public StockStatusDto(Sample sample, int stock, int pendingDemand,
                          String stockLabel, int remainingRate) {
        this.sample = sample;
        this.stock = stock;
        this.pendingDemand = pendingDemand;
        this.stockLabel = stockLabel;
        this.remainingRate = remainingRate;
    }

    public Sample getSample()      { return sample; }
    public int getStock()          { return stock; }
    public int getPendingDemand()  { return pendingDemand; }
    public String getStockLabel()  { return stockLabel; }
    public int getRemainingRate()  { return remainingRate; }
}
