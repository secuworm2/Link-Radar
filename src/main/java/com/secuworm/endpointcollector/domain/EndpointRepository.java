package com.secuworm.endpointcollector.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EndpointRepository {
    private final Map<String, EndpointRecord> endpointMap = new LinkedHashMap<>();

    public synchronized EndpointRecord upsert(
        String endpointUrl,
        String host,
        String sourceUrl,
        String sourceRequestMethod,
        List<RequestHeader> sourceRequestHeaders
    ) {
        long now = System.currentTimeMillis();
        EndpointRecord record = endpointMap.get(endpointUrl);
        if (record == null) {
            EndpointRecord created = new EndpointRecord(
                endpointUrl,
                host,
                sourceUrl,
                sourceRequestMethod,
                sourceRequestHeaders,
                1,
                now,
                now
            );
            endpointMap.put(endpointUrl, created);
            return created;
        }

        record.incrementCount();
        record.setLastSeenAt(now);
        return record;
    }

    public synchronized EndpointRecord upsert(String endpointUrl, String host, String sourceUrl) {
        return upsert(endpointUrl, host, sourceUrl, "", null);
    }

    public synchronized List<EndpointRecord> getAll() {
        return new ArrayList<>(endpointMap.values());
    }

    public synchronized int size() {
        return endpointMap.size();
    }

    public synchronized void clear() {
        endpointMap.clear();
    }
}
