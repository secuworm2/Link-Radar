package com.secuworm.endpointcollector.burpadapter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import com.secuworm.endpointcollector.domain.RequestHeader;
import com.secuworm.endpointcollector.infra.ExtensionLogger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryProvider {
    private final MontoyaApi api;
    private final ExtensionLogger logger;

    public HistoryProvider(MontoyaApi api, ExtensionLogger logger) {
        this.api = api;
        this.logger = logger;
    }

    public List<HistoryItemPayload> getDecodedHistoryItems(String scopeType, List<?> selectedItems) {
        if ("selected".equals(scopeType)) {
            return decodeSelectedItems(selectedItems);
        }
        return decodeProxyHistory();
    }

    private List<HistoryItemPayload> decodeProxyHistory() {
        List<HistoryItemPayload> decoded = new ArrayList<>();
        List<ProxyHttpRequestResponse> historyItems;
        try {
            historyItems = api.proxy().history();
        } catch (Exception ex) {
            logError("proxy history read failed: " + ex.getMessage());
            return decoded;
        }

        for (ProxyHttpRequestResponse item : historyItems) {
            HistoryItemPayload payload = decodeProxyItem(item);
            if (payload != null) {
                decoded.add(payload);
            }
        }
        return decoded;
    }

    private List<HistoryItemPayload> decodeSelectedItems(List<?> selectedItems) {
        List<HistoryItemPayload> decoded = new ArrayList<>();
        if (selectedItems == null) {
            return decoded;
        }
        for (Object item : selectedItems) {
            HistoryItemPayload payload = decodeSelectedItem(item);
            if (payload != null) {
                decoded.add(payload);
            }
        }
        return decoded;
    }

    private HistoryItemPayload decodeSelectedItem(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof HttpRequestResponse) {
            return decodeHttpItem((HttpRequestResponse) item);
        }
        return decodeReflectiveItem(item);
    }

    private HistoryItemPayload decodeProxyItem(ProxyHttpRequestResponse item) {
        if (item == null) {
            return null;
        }
        try {
            HttpRequest request = item.finalRequest();
            HttpResponse response = item.response();
            return decode(request, response);
        } catch (Exception ex) {
            logError("proxy history decode failed: " + ex.getMessage());
            return null;
        }
    }

    private HistoryItemPayload decodeHttpItem(HttpRequestResponse item) {
        if (item == null) {
            return null;
        }
        try {
            HttpRequest request = item.request();
            HttpResponse response = item.response();
            return decode(request, response);
        } catch (Exception ex) {
            logError("selected item decode failed: " + ex.getMessage());
            return null;
        }
    }

    private HistoryItemPayload decodeReflectiveItem(Object item) {
        try {
            HttpRequest request = readRequestReflectively(item);
            HttpResponse response = readResponseReflectively(item);
            if (response == null) {
                return null;
            }
            return decode(request, response);
        } catch (Exception ex) {
            logError("selected item reflective decode failed: " + ex.getMessage());
            return null;
        }
    }

    private HttpRequest readRequestReflectively(Object item) throws Exception {
        Object request = invokeNoArg(item, "request");
        if (request == null) {
            request = invokeNoArg(item, "finalRequest");
        }
        if (request instanceof HttpRequest) {
            return (HttpRequest) request;
        }
        return null;
    }

    private HttpResponse readResponseReflectively(Object item) throws Exception {
        Object response = invokeNoArg(item, "response");
        if (response == null) {
            response = invokeNoArg(item, "finalResponse");
        }
        if (response instanceof HttpResponse) {
            return (HttpResponse) response;
        }
        return null;
    }

    private Object invokeNoArg(Object target, String methodName) throws Exception {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private HistoryItemPayload decode(HttpRequest request, HttpResponse response) {
        if (response == null) {
            return null;
        }
        String sourceUrl = request == null ? "" : safe(request.url());
        String sourceRequestMethod = request == null ? "" : safe(request.method());
        List<RequestHeader> sourceRequestHeaders = extractRequestHeaders(request);
        String contentType = extractContentType(response);
        String responseText = safe(response.bodyToString());
        int responseSize = responseText.getBytes(StandardCharsets.UTF_8).length;
        return new HistoryItemPayload(
            sourceUrl,
            contentType,
            responseText,
            responseSize,
            sourceRequestMethod,
            sourceRequestHeaders
        );
    }

    private List<RequestHeader> extractRequestHeaders(HttpRequest request) {
        List<RequestHeader> headers = new ArrayList<>();
        if (request == null) {
            return headers;
        }
        try {
            for (HttpHeader header : request.headers()) {
                if (header == null) {
                    continue;
                }
                headers.add(new RequestHeader(safe(header.name()), safe(header.value())));
            }
        } catch (Exception ex) {
            logError("request header extraction failed: " + ex.getMessage());
        }
        return headers;
    }

    private String extractContentType(HttpResponse response) {
        for (HttpHeader header : response.headers()) {
            if (header == null || header.name() == null) {
                continue;
            }
            if (!"content-type".equalsIgnoreCase(header.name().trim())) {
                continue;
            }
            String value = safe(header.value()).trim().toLowerCase(Locale.ROOT);
            int delimiter = value.indexOf(';');
            if (delimiter >= 0) {
                return value.substring(0, delimiter).trim();
            }
            return value;
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void logError(String message) {
        if (logger != null) {
            logger.error(message);
        }
    }
}
