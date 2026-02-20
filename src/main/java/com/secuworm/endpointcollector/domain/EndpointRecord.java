package com.secuworm.endpointcollector.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EndpointRecord {
    private final String endpointUrl;
    private final String host;
    private final String sourceUrl;
    private final String sourceRequestMethod;
    private final List<RequestHeader> sourceRequestHeaders;
    private int count;
    private final long firstSeenAt;
    private long lastSeenAt;

    public EndpointRecord(
        String endpointUrl,
        String host,
        String sourceUrl,
        String sourceRequestMethod,
        List<RequestHeader> sourceRequestHeaders,
        int count,
        long firstSeenAt,
        long lastSeenAt
    ) {
        this.endpointUrl = endpointUrl;
        this.host = host;
        this.sourceUrl = sourceUrl;
        this.sourceRequestMethod = sourceRequestMethod == null ? "" : sourceRequestMethod;
        this.sourceRequestHeaders = sourceRequestHeaders == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(sourceRequestHeaders));
        this.count = count;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getHost() {
        return host;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceRequestMethod() {
        return sourceRequestMethod;
    }

    public List<RequestHeader> getSourceRequestHeaders() {
        return sourceRequestHeaders;
    }

    public int getCount() {
        return count;
    }

    public long getFirstSeenAt() {
        return firstSeenAt;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public void incrementCount() {
        count += 1;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
