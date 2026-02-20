package com.secuworm.endpointcollector.domain;

public class EndpointCandidate {
    private final String rawValue;
    private final String sourceUrl;
    private final String contentType;
    private final String matchType;

    public EndpointCandidate(String rawValue, String sourceUrl, String contentType, String matchType) {
        this.rawValue = rawValue;
        this.sourceUrl = sourceUrl;
        this.contentType = contentType;
        this.matchType = matchType;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public String getMatchType() {
        return matchType;
    }
}
