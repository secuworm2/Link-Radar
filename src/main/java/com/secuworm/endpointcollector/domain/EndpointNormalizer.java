package com.secuworm.endpointcollector.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class EndpointNormalizer {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");
    private static final Pattern TEMPLATE_EXPRESSION_PATTERN = Pattern.compile("\\$\\{[^}]+\\}");
    private static final Pattern COLON_PARAM_PATTERN = Pattern.compile("(?<=/):([A-Za-z0-9_]+)");
    private static final Pattern NEXTJS_PARAM_PATTERN = Pattern.compile("\\[(?:\\.\\.\\.)?([A-Za-z0-9_]+)\\]");
    private static final Pattern OPTIONAL_NEXTJS_PARAM_PATTERN = Pattern.compile("\\[\\[(?:\\.\\.\\.)?([A-Za-z0-9_]+)\\]\\]");

    public String normalize(EndpointCandidate candidate, String baseUrl) {
        String rawValue = candidate == null ? null : candidate.getRawValue();
        if (rawValue == null) {
            return null;
        }

        String cleanedValue = trimQuotes(rawValue.trim());
        if (cleanedValue.isEmpty()) {
            return null;
        }
        cleanedValue = decodeHtmlEntities(cleanedValue);
        if (cleanedValue.isEmpty()) {
            return null;
        }
        cleanedValue = normalizeDynamicPathTokens(cleanedValue);
        if (cleanedValue.isEmpty()) {
            return null;
        }
        if (WHITESPACE_PATTERN.matcher(cleanedValue).find()) {
            return null;
        }

        String lowerValue = cleanedValue.toLowerCase(Locale.ROOT);
        if (lowerValue.startsWith("javascript:") || lowerValue.startsWith("mailto:") || lowerValue.startsWith("data:")) {
            return null;
        }

        URI candidateUri = parseUri(toParseSafeValue(cleanedValue));
        if (candidateUri == null) {
            return null;
        }

        if (candidateUri.getScheme() != null && !candidateUri.getScheme().isEmpty()) {
            return normalizeAbsolute(cleanedValue, candidateUri);
        }
        return normalizeRelative(cleanedValue, baseUrl);
    }

    private String normalizeAbsolute(String cleanedValue, URI parsed) {
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        if (parsed.getHost() == null || parsed.getHost().isEmpty()) {
            return null;
        }
        return cleanedValue;
    }

    private String normalizeRelative(String cleanedValue, String baseUrl) {
        if (cleanedValue.startsWith("//")) {
            return null;
        }
        URI baseUri = parseUri(baseUrl == null ? "" : baseUrl);
        if (baseUri == null) {
            return null;
        }
        String scheme = baseUri.getScheme() == null ? "" : baseUri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        if (baseUri.getHost() == null || baseUri.getHost().isEmpty()) {
            return null;
        }

        String openToken = "__LR_OPEN_TOKEN__";
        String closeToken = "__LR_CLOSE_TOKEN__";
        String resolvableValue = cleanedValue
            .replace("{", openToken)
            .replace("}", closeToken);
        URI resolvedUri;
        try {
            resolvedUri = baseUri.resolve(resolvableValue);
        } catch (Exception ex) {
            return null;
        }
        if (resolvedUri == null) {
            return null;
        }

        String resolvedScheme = resolvedUri.getScheme() == null ? scheme : resolvedUri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(resolvedScheme) && !"https".equals(resolvedScheme)) {
            return null;
        }

        String resolvedHost = resolvedUri.getHost();
        if (resolvedHost == null || resolvedHost.isEmpty()) {
            resolvedHost = baseUri.getHost();
        }
        if (resolvedHost == null || resolvedHost.isEmpty()) {
            return null;
        }

        int port = resolvedUri.getPort();
        if (port <= 0) {
            port = baseUri.getPort();
        }
        String portPart = "";
        if (port > 0) {
            boolean omitHttp = "http".equals(resolvedScheme) && port == 80;
            boolean omitHttps = "https".equals(resolvedScheme) && port == 443;
            if (!omitHttp && !omitHttps) {
                portPart = ":" + port;
            }
        }

        String rawPath = resolvedUri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            rawPath = "/";
        }
        rawPath = normalizeRawPath(rawPath);
        rawPath = rawPath.replace(openToken, "{").replace(closeToken, "}");

        StringBuilder normalized = new StringBuilder();
        normalized.append(resolvedScheme).append("://").append(resolvedHost).append(portPart).append(rawPath);
        String rawQuery = resolvedUri.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            rawQuery = rawQuery.replace(openToken, "{").replace(closeToken, "}");
            normalized.append("?").append(rawQuery);
        }
        String rawFragment = resolvedUri.getRawFragment();
        if (rawFragment != null && !rawFragment.isEmpty()) {
            rawFragment = rawFragment.replace(openToken, "{").replace(closeToken, "}");
            normalized.append("#").append(rawFragment);
        }
        return normalized.toString();
    }

    private String normalizeRawPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }

        boolean absolute = rawPath.startsWith("/");
        boolean trailingSlash = rawPath.endsWith("/");
        String[] segments = rawPath.split("/");
        List<String> normalized = new ArrayList<>();

        for (String segment : segments) {
            if (segment == null || segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!normalized.isEmpty()) {
                    normalized.remove(normalized.size() - 1);
                }
                continue;
            }
            normalized.add(segment);
        }

        StringBuilder builder = new StringBuilder();
        if (absolute) {
            builder.append("/");
        }
        for (int i = 0; i < normalized.size(); i++) {
            if (i > 0) {
                builder.append("/");
            }
            builder.append(normalized.get(i));
        }
        if (trailingSlash && !normalized.isEmpty() && builder.charAt(builder.length() - 1) != '/') {
            builder.append("/");
        }

        String value = builder.toString();
        if (value.isEmpty()) {
            return absolute ? "/" : "";
        }
        return value;
    }

    private URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String trimQuotes(String value) {
        String trimmed = value;
        while (!trimmed.isEmpty() && (trimmed.charAt(0) == '\'' || trimmed.charAt(0) == '\"')) {
            trimmed = trimmed.substring(1);
        }
        while (!trimmed.isEmpty() && (trimmed.charAt(trimmed.length() - 1) == '\'' || trimmed.charAt(trimmed.length() - 1) == '\"')) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String decodeHtmlEntities(String value) {
        StringBuilder decoded = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            char current = value.charAt(i);
            if (current != '&') {
                decoded.append(current);
                i += 1;
                continue;
            }

            DecodedEntity decodedEntity = tryDecodeEntityAt(value, i);
            if (decodedEntity == null) {
                decoded.append(current);
                i += 1;
                continue;
            }
            decoded.append(decodedEntity.replacement);
            i = decodedEntity.nextIndex;
        }
        return decoded.toString();
    }

    private DecodedEntity tryDecodeEntityAt(String value, int ampersandIndex) {
        int cursor = ampersandIndex + 1;
        if (cursor >= value.length()) {
            return null;
        }

        char first = value.charAt(cursor);
        if (first == '#') {
            return tryDecodeNumericEntityAt(value, cursor);
        }
        if (!Character.isLetter(first)) {
            return null;
        }

        int tokenStart = cursor;
        while (cursor < value.length() && Character.isLetter(value.charAt(cursor))) {
            cursor += 1;
        }
        boolean hasSemicolon = cursor < value.length() && value.charAt(cursor) == ';';
        String entity = value.substring(tokenStart, cursor);
        String replacement = decodeEntity(entity);
        if (replacement == null) {
            return null;
        }
        if (!hasSemicolon && cursor < value.length() && Character.isLetterOrDigit(value.charAt(cursor))) {
            return null;
        }

        int nextIndex = hasSemicolon ? cursor + 1 : cursor;
        return new DecodedEntity(replacement, nextIndex);
    }

    private DecodedEntity tryDecodeNumericEntityAt(String value, int hashIndex) {
        int cursor = hashIndex + 1;
        if (cursor >= value.length()) {
            return null;
        }

        boolean hexadecimal = value.charAt(cursor) == 'x' || value.charAt(cursor) == 'X';
        if (hexadecimal) {
            cursor += 1;
            int hexStart = cursor;
            while (cursor < value.length() && isHexDigit(value.charAt(cursor))) {
                cursor += 1;
            }
            if (cursor <= hexStart) {
                return null;
            }
        } else {
            int digitStart = cursor;
            while (cursor < value.length() && Character.isDigit(value.charAt(cursor))) {
                cursor += 1;
            }
            if (cursor <= digitStart) {
                return null;
            }
        }

        boolean hasSemicolon = cursor < value.length() && value.charAt(cursor) == ';';
        String entity = value.substring(hashIndex, cursor);
        String replacement = decodeEntity(entity);
        if (replacement == null) {
            return null;
        }
        int nextIndex = hasSemicolon ? cursor + 1 : cursor;
        return new DecodedEntity(replacement, nextIndex);
    }

    private String decodeEntity(String entity) {
        if (entity == null || entity.isEmpty()) {
            return null;
        }
        String normalized = entity.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
            case "#39":
                return "'";
            default:
                break;
        }

        if (!normalized.startsWith("#")) {
            return null;
        }
        try {
            int codePoint;
            if (normalized.startsWith("#x")) {
                codePoint = Integer.parseInt(normalized.substring(2), 16);
            } else {
                codePoint = Integer.parseInt(normalized.substring(1), 10);
            }
            if (!Character.isValidCodePoint(codePoint)) {
                return null;
            }
            return new String(Character.toChars(codePoint));
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeDynamicPathTokens(String value) {
        String normalized = value;
        normalized = TEMPLATE_EXPRESSION_PATTERN.matcher(normalized).replaceAll("{var}");
        normalized = OPTIONAL_NEXTJS_PARAM_PATTERN.matcher(normalized).replaceAll("{$1}");
        normalized = NEXTJS_PARAM_PATTERN.matcher(normalized).replaceAll("{$1}");
        normalized = COLON_PARAM_PATTERN.matcher(normalized).replaceAll("{$1}");
        return normalized;
    }

    private String toParseSafeValue(String value) {
        return value.replace("{", "x").replace("}", "x");
    }

    private boolean isHexDigit(char character) {
        return Character.digit(character, 16) >= 0;
    }

    private static class DecodedEntity {
        private final String replacement;
        private final int nextIndex;

        private DecodedEntity(String replacement, int nextIndex) {
            this.replacement = replacement;
            this.nextIndex = nextIndex;
        }
    }
}
