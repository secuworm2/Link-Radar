package com.secuworm.endpointcollector.domain;

import com.secuworm.endpointcollector.infra.AppConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointExtractor {
    private static final Pattern ABSOLUTE_URL_PATTERN = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_URL_PATTERN = Pattern.compile("(?:^|(?<=[\\s\\\"'`=(:\\[,>{]))/(?!/)[^\\s\\\"'<>),;]+");
    private static final Pattern DOT_RELATIVE_URL_PATTERN = Pattern.compile("(?:^|(?<=[\\s\\\"'`=(:\\[,>{]))(?:\\.\\./|\\./)[^\\s\\\"'<>),;]+");
    private static final Pattern FETCH_CALL_PATTERN = Pattern.compile("\\bfetch\\s*\\(\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern AXIOS_METHOD_PATTERN = Pattern.compile("\\baxios\\s*\\.\\s*(?:get|post|put|patch|delete|head|options)\\s*\\(\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern AXIOS_CONFIG_URL_PATTERN = Pattern.compile("\\baxios\\s*\\(\\s*\\{[^\\}]*?\\burl\\s*:\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern XHR_OPEN_PATTERN = Pattern.compile("\\.open\\s*\\(\\s*(?:[\"'`][A-Za-z]+[\"'`]\\s*,\\s*)?([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEW_URL_PATTERN = Pattern.compile("\\bnew\\s+URL\\s*\\(\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRAMEWORK_ROUTE_PATTERN = Pattern.compile("\\b(?:app|router|fastify)\\s*\\.\\s*(?:get|post|put|patch|delete|head|options|all|use|route)\\s*\\(\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern FASTIFY_ROUTE_OBJECT_PATTERN = Pattern.compile("\\bfastify\\s*\\.\\s*route\\s*\\(\\s*\\{[^\\}]*?\\b(?:url|path)\\s*:\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REACT_ROUTE_PATTERN = Pattern.compile("<Route[^>]*\\bpath\\s*=\\s*([\"'`])([^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_OBJECT_PATH_PATTERN = Pattern.compile("\\bpath\\s*:\\s*([\"'`])(/[^\"'`]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_FLAG_ONLY_PATTERN = Pattern.compile("^/[dgimsuvy]{1,8}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_FLAG_TEST_PATTERN = Pattern.compile("^/[dgimsuvy]{1,8}\\.test\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_FLAGS_PATTERN = Pattern.compile("^[dgimsuvy]{1,8}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_META_FRAGMENT_PATTERN = Pattern.compile("[\\[\\]{}()^$*+?|\\\\]");
    private static final Pattern URL_PATH_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]+$");
    private static final Pattern SCRIPT_BLOCK_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>(.*?)</script>");
    private static final String[] REGEX_CONSUMER_METHODS = new String[]{
        "match",
        "matchall",
        "replace",
        "replaceall",
        "search",
        "split",
        "test",
        "exec"
    };
    private static final String[] NOISE_PREFIXES = new String[]{"javascript:", "mailto:"};
    private static final String TRAILING_TRIM_CHARS = ".,;:!?)\\";
    private static final String[] JAVASCRIPT_CONTENT_TYPES = new String[]{
        "application/javascript",
        "text/javascript",
        "application/x-javascript"
    };

    private final JsAstEndpointExtractor jsAstEndpointExtractor;

    public EndpointExtractor() {
        this.jsAstEndpointExtractor = new JsAstEndpointExtractor();
    }

    public List<EndpointCandidate> extract(String responseText, String contentType, String sourceUrl) {
        String normalizedContentType = normalizeContentType(contentType);
        String effectiveContentType = resolveEffectiveContentType(normalizedContentType, responseText, sourceUrl);
        if (responseText == null || responseText.isEmpty()) {
            return new ArrayList<>();
        }

        List<EndpointCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<JsAstEndpointExtractor.SourceRange> regexLiteralRanges = collectRegexLiteralRanges(responseText, effectiveContentType);

        collectCandidates(candidates, seen, ABSOLUTE_URL_PATTERN.matcher(responseText), responseText, "absolute", effectiveContentType, sourceUrl, regexLiteralRanges);
        collectCandidates(candidates, seen, RELATIVE_URL_PATTERN.matcher(responseText), responseText, "relative", effectiveContentType, sourceUrl, regexLiteralRanges);
        collectCandidates(candidates, seen, DOT_RELATIVE_URL_PATTERN.matcher(responseText), responseText, "relative", effectiveContentType, sourceUrl, regexLiteralRanges);
        collectGroupCandidates(candidates, seen, FETCH_CALL_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, AXIOS_METHOD_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, AXIOS_CONFIG_URL_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, XHR_OPEN_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, NEW_URL_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, FRAMEWORK_ROUTE_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, FASTIFY_ROUTE_OBJECT_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, REACT_ROUTE_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);
        collectGroupCandidates(candidates, seen, ROUTE_OBJECT_PATH_PATTERN.matcher(responseText), 2, effectiveContentType, sourceUrl);

        for (String javascriptSource : collectJavaScriptSources(responseText, effectiveContentType)) {
            List<String> astCandidates = jsAstEndpointExtractor.extract(javascriptSource);
            collectListCandidates(candidates, seen, astCandidates, effectiveContentType, sourceUrl);
        }

        return candidates;
    }

    private void collectCandidates(
        List<EndpointCandidate> target,
        Set<String> seen,
        Matcher matcher,
        String responseText,
        String matchType,
        String contentType,
        String sourceUrl,
        List<JsAstEndpointExtractor.SourceRange> regexLiteralRanges
    ) {
        while (matcher.find()) {
            if (isInsideRegexLiteral(matcher.start(), regexLiteralRanges)) {
                continue;
            }
            if (isRegexConsumerContext(responseText, matcher.start(), matchType, contentType)) {
                continue;
            }
            String cleaned = cleanMatch(matcher.group());
            if (cleaned == null) {
                continue;
            }
            String lowerCleaned = cleaned.toLowerCase(Locale.ROOT);
            if (startsWithAny(lowerCleaned, NOISE_PREFIXES)) {
                continue;
            }
            if ("relative".equals(matchType) && cleaned.startsWith("//")) {
                continue;
            }
            if (isLikelyRegexFragment(cleaned, matchType)) {
                continue;
            }

            String dedupeKey = cleaned + "|" + matchType;
            if (!seen.add(dedupeKey)) {
                continue;
            }
            target.add(new EndpointCandidate(cleaned, sourceUrl, contentType, matchType));
        }
    }

    private void collectGroupCandidates(
        List<EndpointCandidate> target,
        Set<String> seen,
        Matcher matcher,
        int groupIndex,
        String contentType,
        String sourceUrl
    ) {
        while (matcher.find()) {
            if (groupIndex > matcher.groupCount()) {
                continue;
            }
            String value = matcher.group(groupIndex);
            String matchType = detectMatchType(value);
            collectSingleCandidate(target, seen, value, matchType, contentType, sourceUrl);
        }
    }

    private void collectListCandidates(
        List<EndpointCandidate> target,
        Set<String> seen,
        List<String> values,
        String contentType,
        String sourceUrl
    ) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String matchType = detectMatchType(value);
            collectSingleCandidate(target, seen, value, matchType, contentType, sourceUrl);
        }
    }

    private void collectSingleCandidate(
        List<EndpointCandidate> target,
        Set<String> seen,
        String value,
        String matchType,
        String contentType,
        String sourceUrl
    ) {
        String cleaned = cleanMatch(value);
        if (cleaned == null) {
            return;
        }
        String lowerCleaned = cleaned.toLowerCase(Locale.ROOT);
        if (startsWithAny(lowerCleaned, NOISE_PREFIXES)) {
            return;
        }
        if ("relative".equals(matchType) && cleaned.startsWith("//")) {
            return;
        }
        if (isLikelyRegexFragment(cleaned, matchType)) {
            return;
        }
        String dedupeKey = cleaned + "|" + matchType;
        if (!seen.add(dedupeKey)) {
            return;
        }
        target.add(new EndpointCandidate(cleaned, sourceUrl, contentType, matchType));
    }

    private String detectMatchType(String value) {
        if (value == null) {
            return "relative";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return "absolute";
        }
        return "relative";
    }

    private List<String> collectJavaScriptSources(String responseText, String normalizedContentType) {
        List<String> sources = new ArrayList<>();
        if (isJavaScriptContentType(normalizedContentType)) {
            sources.add(responseText);
        }
        if (!"text/html".equals(normalizedContentType)) {
            return sources;
        }
        Matcher scriptMatcher = SCRIPT_BLOCK_PATTERN.matcher(responseText);
        while (scriptMatcher.find()) {
            String scriptBlock = scriptMatcher.group(1);
            if (scriptBlock == null || scriptBlock.trim().isEmpty()) {
                continue;
            }
            sources.add(scriptBlock);
        }
        return sources;
    }

    private boolean isJavaScriptContentType(String contentType) {
        for (String supportedType : JAVASCRIPT_CONTENT_TYPES) {
            if (supportedType.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    private String resolveEffectiveContentType(String normalizedContentType, String responseText, String sourceUrl) {
        if (AppConfig.SUPPORTED_CONTENT_TYPES.contains(normalizedContentType)) {
            return normalizedContentType;
        }
        if (normalizedContentType.contains("json")) {
            return "application/json";
        }
        if (normalizedContentType.contains("javascript") || normalizedContentType.contains("ecmascript")) {
            return "application/javascript";
        }
        if (normalizedContentType.contains("html")) {
            return "text/html";
        }
        if (normalizedContentType.contains("text/")) {
            return "text/plain";
        }

        String body = responseText == null ? "" : responseText.trim();
        if (looksLikeJson(body)) {
            return "application/json";
        }
        if (looksLikeHtml(body)) {
            return "text/html";
        }
        if (looksLikeJavaScript(body, sourceUrl)) {
            return "application/javascript";
        }
        if (!normalizedContentType.isEmpty()) {
            return normalizedContentType;
        }
        return "application/octet-stream";
    }

    private boolean looksLikeJson(String body) {
        if (body.isEmpty()) {
            return false;
        }
        if (!(body.startsWith("{") || body.startsWith("["))) {
            return false;
        }
        return body.indexOf(':') >= 0 || body.indexOf('{') >= 0;
    }

    private boolean looksLikeHtml(String body) {
        if (body.isEmpty()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype html")
            || lower.startsWith("<html")
            || lower.contains("<script");
    }

    private boolean looksLikeJavaScript(String body, String sourceUrl) {
        String lowerSourceUrl = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (lowerSourceUrl.endsWith(".js") || lowerSourceUrl.endsWith(".mjs")) {
            return true;
        }
        if (body.isEmpty()) {
            return false;
        }
        String lowerBody = body.toLowerCase(Locale.ROOT);
        return lowerBody.contains("function ")
            || lowerBody.contains("=>")
            || lowerBody.contains("window.")
            || lowerBody.contains("document.")
            || lowerBody.contains("var ")
            || lowerBody.contains("const ")
            || lowerBody.contains("let ");
    }

    private List<JsAstEndpointExtractor.SourceRange> collectRegexLiteralRanges(String responseText, String normalizedContentType) {
        if (!isJavaScriptContentType(normalizedContentType)) {
            return new ArrayList<>();
        }
        return jsAstEndpointExtractor.extractRegexLiteralRanges(responseText);
    }

    private boolean isInsideRegexLiteral(int matchStart, List<JsAstEndpointExtractor.SourceRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return false;
        }
        for (JsAstEndpointExtractor.SourceRange range : ranges) {
            if (range != null && range.contains(matchStart)) {
                return true;
            }
        }
        return false;
    }

    private String cleanMatch(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        } else {
            cleaned = trimQuotes(cleaned);
        }
        while (!cleaned.isEmpty() && TRAILING_TRIM_CHARS.indexOf(cleaned.charAt(cleaned.length() - 1)) >= 0) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isEmpty() || "/".equals(cleaned)) {
            return null;
        }
        return cleaned;
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

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int delimiter = contentType.indexOf(';');
        if (delimiter >= 0) {
            return contentType.substring(0, delimiter).trim().toLowerCase(Locale.ROOT);
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyRegexFragment(String value, String matchType) {
        if (!"relative".equals(matchType) || value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || !normalized.startsWith("/") || normalized.startsWith("//")) {
            return false;
        }

        String withoutQuery = stripQueryOnly(normalized);
        if (withoutQuery.isEmpty()) {
            return false;
        }
        if (REGEX_FLAG_ONLY_PATTERN.matcher(withoutQuery).matches()) {
            return true;
        }
        if (REGEX_FLAG_TEST_PATTERN.matcher(withoutQuery).matches()) {
            return true;
        }
        if (withoutQuery.startsWith("/(") || withoutQuery.startsWith("/[") || withoutQuery.startsWith("/^")) {
            return true;
        }
        if (isRegexBodyWithTrailingFlags(normalized)) {
            return true;
        }
        if (isRegexBodyWithNonPathSuffix(normalized)) {
            return true;
        }
        if (isRegexBodyWithoutFlags(normalized)) {
            return true;
        }
        if (isOpenRegexFragment(normalized)) {
            return true;
        }
        if (withoutQuery.contains("\\") || withoutQuery.contains("|") || withoutQuery.contains(".test(")) {
            return true;
        }
        return false;
    }

    private String stripQueryOnly(String value) {
        int queryIndex = value.indexOf('?');
        int end = value.length();
        if (queryIndex >= 0 && queryIndex < end) {
            end = queryIndex;
        }
        return value.substring(0, end);
    }

    private boolean isRegexBodyWithTrailingFlags(String value) {
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash <= 0 || lastSlash >= value.length() - 1) {
            return false;
        }
        String flags = value.substring(lastSlash + 1);
        if (!REGEX_FLAGS_PATTERN.matcher(flags).matches()) {
            return false;
        }
        String regexBody = value.substring(1, lastSlash);
        if (regexBody.isEmpty()) {
            return false;
        }
        if (regexBody.length() == 1) {
            return true;
        }
        if (regexBody.indexOf('/') < 0 && !URL_PATH_SEGMENT_PATTERN.matcher(regexBody).matches()) {
            return true;
        }
        return containsRegexMeta(regexBody);
    }

    private boolean isRegexBodyWithoutFlags(String value) {
        int closingSlash = value.lastIndexOf('/');
        if (closingSlash <= 1 || closingSlash != value.length() - 1) {
            return false;
        }
        String regexBody = value.substring(1, closingSlash);
        if (regexBody.isEmpty()) {
            return false;
        }
        if (regexBody.indexOf('/') >= 0) {
            return false;
        }
        if (regexBody.length() == 1) {
            return true;
        }
        if (containsRegexMeta(regexBody)) {
            return true;
        }
        return !URL_PATH_SEGMENT_PATTERN.matcher(regexBody).matches();
    }

    private boolean isRegexBodyWithNonPathSuffix(String value) {
        int closingSlash = value.indexOf('/', 1);
        if (closingSlash <= 1 || closingSlash >= value.length() - 1) {
            return false;
        }
        String regexBody = value.substring(1, closingSlash);
        if (regexBody.isEmpty()) {
            return false;
        }
        if (!containsRegexMeta(regexBody) && regexBody.length() > 1) {
            return false;
        }
        String suffix = value.substring(closingSlash + 1);
        boolean hasAlphaNumeric = false;
        for (int i = 0; i < suffix.length(); i++) {
            char character = suffix.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                hasAlphaNumeric = true;
                break;
            }
        }
        return !hasAlphaNumeric;
    }

    private boolean isOpenRegexFragment(String value) {
        int nextSlash = value.indexOf('/', 1);
        if (nextSlash >= 0) {
            return false;
        }
        String fragment = value.substring(1);
        if (fragment.isEmpty()) {
            return false;
        }
        int queryMark = fragment.indexOf('?');
        if (queryMark >= 0 && fragment.indexOf('=', queryMark + 1) >= 0) {
            return false;
        }
        if (fragment.contains(".test")) {
            return true;
        }
        return REGEX_META_FRAGMENT_PATTERN.matcher(fragment).find();
    }

    private boolean containsRegexMeta(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '[' || ch == ']' || ch == '(' || ch == ')' || ch == '{' || ch == '}'
                || ch == '^' || ch == '$' || ch == '*' || ch == '+' || ch == '?' || ch == '|' || ch == '.'
                || ch == ',' || ch == '%' || ch == '~') {
                return true;
            }
        }
        return value.indexOf('-') >= 0;
    }

    private boolean isRegexConsumerContext(String responseText, int matchStart, String matchType, String contentType) {
        if (!"relative".equals(matchType) || !isJavaScriptContentType(contentType)) {
            return false;
        }
        if (responseText == null || responseText.isEmpty() || matchStart <= 0 || matchStart > responseText.length()) {
            return false;
        }

        int index = matchStart - 1;
        while (index >= 0 && Character.isWhitespace(responseText.charAt(index))) {
            index -= 1;
        }
        if (index < 0 || responseText.charAt(index) != '(') {
            return false;
        }

        index -= 1;
        while (index >= 0 && Character.isWhitespace(responseText.charAt(index))) {
            index -= 1;
        }
        if (index < 0) {
            return false;
        }
        int methodEnd = index;
        while (index >= 0 && isIdentifierPart(responseText.charAt(index))) {
            index -= 1;
        }
        if (methodEnd < index + 1) {
            return false;
        }
        String methodName = responseText.substring(index + 1, methodEnd + 1).toLowerCase(Locale.ROOT);

        while (index >= 0 && Character.isWhitespace(responseText.charAt(index))) {
            index -= 1;
        }
        if (index < 0 || responseText.charAt(index) != '.') {
            return false;
        }

        for (String consumer : REGEX_CONSUMER_METHODS) {
            if (consumer.equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
}
