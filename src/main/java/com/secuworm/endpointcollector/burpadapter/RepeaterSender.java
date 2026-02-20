package com.secuworm.endpointcollector.burpadapter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.secuworm.endpointcollector.domain.EndpointRecord;
import com.secuworm.endpointcollector.domain.RequestHeader;
import com.secuworm.endpointcollector.infra.ExtensionLogger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class RepeaterSender {
    private static final Set<String> BLOCKED_HEADERS = Set.of(
        "host",
        "content-length",
        ":authority",
        ":method",
        ":path",
        ":scheme"
    );

    private final MontoyaApi api;
    private final ExtensionLogger logger;

    public RepeaterSender(MontoyaApi api, ExtensionLogger logger) {
        this.api = api;
        this.logger = logger;
    }

    public SendResult sendToRepeater(EndpointRecord record) {
        if (record == null) {
            return SendResult.failure("No record selected.");
        }

        String endpointUrl = safe(record.getEndpointUrl()).trim();
        if (endpointUrl.isEmpty()) {
            return SendResult.failure("Endpoint URL is empty.");
        }

        try {
            HttpRequest request = HttpRequest.httpRequestFromUrl(endpointUrl);
            String sourceMethod = normalizeMethod(record.getSourceRequestMethod());
            if (!sourceMethod.isEmpty()) {
                request = request.withMethod(sourceMethod);
            }

            for (RequestHeader sourceHeader : record.getSourceRequestHeaders()) {
                if (sourceHeader == null) {
                    continue;
                }
                String name = safe(sourceHeader.getName()).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String normalizedName = name.toLowerCase(Locale.ROOT);
                if (BLOCKED_HEADERS.contains(normalizedName)) {
                    continue;
                }
                String value = safe(sourceHeader.getValue());
                if (request.hasHeader(name)) {
                    request = request.withUpdatedHeader(name, value);
                } else {
                    request = request.withAddedHeader(name, value);
                }
            }

            request = request.withUpdatedHeader("Host", resolveHostHeaderValue(request.httpService()));
            String tabName = resolveRepeaterTabName(endpointUrl, request.httpService());
            api.repeater().sendToRepeater(request, tabName);
            return SendResult.success("Sent to Repeater: " + endpointUrl);
        } catch (Exception ex) {
            logError("send to repeater failed: " + ex.getMessage());
            return SendResult.failure("Send to Repeater failed.");
        }
    }

    private String resolveHostHeaderValue(HttpService service) {
        if (service == null) {
            return "";
        }
        String host = safe(service.host());
        int port = service.port();
        boolean secure = service.secure();
        if (port <= 0) {
            return host;
        }
        if (!secure && port == 80) {
            return host;
        }
        if (secure && port == 443) {
            return host;
        }
        return host + ":" + port;
    }

    private String normalizeMethod(String sourceMethod) {
        String method = safe(sourceMethod).trim();
        if (method.isEmpty()) {
            return "";
        }
        return method.toUpperCase(Locale.ROOT);
    }

    private String resolveRepeaterTabName(String endpointUrl, HttpService service) {
        String fromPath = extractLastPathSegment(endpointUrl);
        if (!fromPath.isEmpty()) {
            return fromPath;
        }
        if (service != null) {
            String host = safe(service.host()).trim();
            if (!host.isEmpty()) {
                return host;
            }
        }
        return "Link Radar";
    }

    private String extractLastPathSegment(String endpointUrl) {
        try {
            URI uri = new URI(endpointUrl);
            String path = safe(uri.getPath()).trim();
            if (path.isEmpty() || "/".equals(path)) {
                return "";
            }
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                String segment = safe(segments[i]).trim();
                if (!segment.isEmpty()) {
                    return segment;
                }
            }
            return "";
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void logError(String message) {
        if (logger == null) {
            return;
        }
        logger.error(message);
    }

    public static class SendResult {
        private final boolean success;
        private final String message;

        private SendResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public static SendResult success(String message) {
            return new SendResult(true, message);
        }

        public static SendResult failure(String message) {
            return new SendResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
