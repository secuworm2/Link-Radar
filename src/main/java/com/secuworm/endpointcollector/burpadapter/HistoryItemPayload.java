package com.secuworm.endpointcollector.burpadapter;

import com.secuworm.endpointcollector.domain.RequestHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryItemPayload {
    private final String sourceUrl;
    private final String contentType;
    private final String responseText;
    private final int responseSizeBytes;
    private final String sourceRequestMethod;
    private final List<RequestHeader> sourceRequestHeaders;

    public HistoryItemPayload(
        String sourceUrl,
        String contentType,
        String responseText,
        int responseSizeBytes,
        String sourceRequestMethod,
        List<RequestHeader> sourceRequestHeaders
    ) {
        this.sourceUrl = sourceUrl;
        this.contentType = contentType;
        this.responseText = responseText;
        this.responseSizeBytes = responseSizeBytes;
        this.sourceRequestMethod = sourceRequestMethod == null ? "" : sourceRequestMethod;
        this.sourceRequestHeaders = sourceRequestHeaders == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(sourceRequestHeaders));
    }

    public HistoryItemPayload(String sourceUrl, String contentType, String responseText, int responseSizeBytes) {
        this(sourceUrl, contentType, responseText, responseSizeBytes, "", null);
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public String getResponseText() {
        return responseText;
    }

    public int getResponseSizeBytes() {
        return responseSizeBytes;
    }

    public String getSourceRequestMethod() {
        return sourceRequestMethod;
    }

    public List<RequestHeader> getSourceRequestHeaders() {
        return sourceRequestHeaders;
    }
}
