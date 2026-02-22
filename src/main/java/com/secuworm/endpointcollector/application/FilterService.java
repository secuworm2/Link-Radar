package com.secuworm.endpointcollector.application;

import com.secuworm.endpointcollector.domain.EndpointRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterService {
    public List<EndpointRecord> filter(List<EndpointRecord> records, String keyword) {
        return filter(records, keyword, false);
    }

    public List<EndpointRecord> filter(List<EndpointRecord> records, String keyword, boolean regexEnabled) {
        List<EndpointRecord> items = records == null ? new ArrayList<>() : new ArrayList<>(records);
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return items;
        }
        FilterQuery query = parseQuery(keyword, regexEnabled);
        if (!query.hasAnyTerm()) {
            return items;
        }

        List<EndpointRecord> filtered = new ArrayList<>();
        for (EndpointRecord record : items) {
            if (record == null) {
                continue;
            }
            if (matches(record, query)) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    private boolean matches(EndpointRecord record, FilterQuery query) {
        String endpointUrl = record.getEndpointUrl() == null ? "" : record.getEndpointUrl();
        for (Pattern include : query.includePatterns) {
            if (!include.matcher(endpointUrl).find()) {
                return false;
            }
        }
        for (Pattern exclude : query.excludePatterns) {
            if (exclude.matcher(endpointUrl).find()) {
                return false;
            }
        }
        return true;
    }

    private FilterQuery parseQuery(String queryText, boolean regexEnabled) {
        FilterQuery query = new FilterQuery();
        for (String rawTerm : tokenize(queryText)) {
            String term = rawTerm == null ? "" : rawTerm.trim();
            if (term.isEmpty()) {
                continue;
            }
            boolean exclude = term.startsWith("!") && term.length() > 1;
            String token = exclude ? term.substring(1) : term;
            Pattern pattern = compilePattern(token, regexEnabled);
            if (pattern == null) {
                continue;
            }
            if (exclude) {
                query.excludePatterns.add(pattern);
            } else {
                query.includePatterns.add(pattern);
            }
        }
        return query;
    }

    private Pattern compilePattern(String token, boolean regexEnabled) {
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        String source = regexEnabled ? token : globToRegex(token);
        try {
            return Pattern.compile(source, flags);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }

    private String globToRegex(String token) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch == '*') {
                regex.append(".*");
                continue;
            }
            if (ch == '?') {
                regex.append('.');
                continue;
            }
            if ("\\.^$|()[]{}+".indexOf(ch) >= 0) {
                regex.append('\\');
            }
            regex.append(ch);
        }
        return regex.toString();
    }

    private List<String> tokenize(String value) {
        List<String> tokens = new ArrayList<>();
        if (value == null) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (Character.isWhitespace(ch) && !inQuote) {
                flushToken(tokens, current);
                continue;
            }
            current.append(ch);
        }
        flushToken(tokens, current);
        return tokens;
    }

    private void flushToken(List<String> tokens, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static class FilterQuery {
        private final List<Pattern> includePatterns = new ArrayList<>();
        private final List<Pattern> excludePatterns = new ArrayList<>();

        private boolean hasAnyTerm() {
            return !includePatterns.isEmpty() || !excludePatterns.isEmpty();
        }
    }
}
