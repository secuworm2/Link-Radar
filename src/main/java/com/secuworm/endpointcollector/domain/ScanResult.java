package com.secuworm.endpointcollector.domain;

public class ScanResult {
    private final int totalItems;
    private final int processedItems;
    private final int totalCandidates;
    private final int uniqueEndpoints;
    private final int errorCount;
    private final long durationMs;

    public ScanResult(
        int totalItems,
        int processedItems,
        int totalCandidates,
        int uniqueEndpoints,
        int errorCount,
        long durationMs
    ) {
        this.totalItems = totalItems;
        this.processedItems = processedItems;
        this.totalCandidates = totalCandidates;
        this.uniqueEndpoints = uniqueEndpoints;
        this.errorCount = errorCount;
        this.durationMs = durationMs;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public int getTotalCandidates() {
        return totalCandidates;
    }

    public int getUniqueEndpoints() {
        return uniqueEndpoints;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
