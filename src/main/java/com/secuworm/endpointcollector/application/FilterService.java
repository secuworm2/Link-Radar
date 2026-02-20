package com.secuworm.endpointcollector.application;

import com.secuworm.endpointcollector.domain.EndpointRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FilterService {
    public List<EndpointRecord> filter(List<EndpointRecord> records, String keyword) {
        List<EndpointRecord> items = records == null ? new ArrayList<>() : new ArrayList<>(records);
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return items;
        }

        List<EndpointRecord> filtered = new ArrayList<>();
        for (EndpointRecord record : items) {
            if (record == null) {
                continue;
            }
            if (matches(record, normalizedKeyword)) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    private boolean matches(EndpointRecord record, String keyword) {
        String endpointUrl = record.getEndpointUrl() == null ? "" : record.getEndpointUrl();
        String host = record.getHost() == null ? "" : record.getHost();
        String sourceUrl = record.getSourceUrl() == null ? "" : record.getSourceUrl();
        String haystack = endpointUrl + " " + host + " " + sourceUrl;
        return normalize(haystack).contains(keyword);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
