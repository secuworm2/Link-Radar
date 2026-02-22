package com.secuworm.endpointcollector;

import com.secuworm.endpointcollector.application.ExportService;
import com.secuworm.endpointcollector.application.FilterService;
import com.secuworm.endpointcollector.application.ScanService;
import com.secuworm.endpointcollector.burpadapter.HistoryItemPayload;
import com.secuworm.endpointcollector.domain.EndpointCandidate;
import com.secuworm.endpointcollector.domain.EndpointExtractor;
import com.secuworm.endpointcollector.domain.EndpointNormalizer;
import com.secuworm.endpointcollector.domain.EndpointRecord;
import com.secuworm.endpointcollector.domain.EndpointRepository;
import com.secuworm.endpointcollector.domain.JsAstEndpointExtractor;
import com.secuworm.endpointcollector.domain.ScanResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TaskM05Regression {
    public static void main(String[] args) throws Exception {
        testItemFailureIsolation();
        testSampleDedupeCountFilterAndCsv();
        testAdvancedFilterSyntaxAndEndpointOnlyScope();
        testHtmlEntityDecoding();
        testHtmlEntityDecodingWithoutSemicolon();
        testDotRelativePathExtractionAndNormalization();
        testDotRelativePathAboveRootClamping();
        testDynamicJavaScriptRoutingAndFrameworkSignatures();
        testLargeJavascriptPayloadExtraction();
        testJavaScriptDivisionFalsePositiveSuppression();
        testRegexLiteralFalsePositiveSuppression();
        testWorkspaceAppJsRegexRangeCoverageIfPresent();
        testWorkspaceAppJsSampleIfPresent();
        testWorkspaceExternalPreSampleIfPresent();
        testWorkspaceTplSampleIfPresent();
        testWorkspaceRequestJsonSampleIfPresent();
    }

    private static void testItemFailureIsolation() {
        ScanService scanService = new ScanService(
            new ThrowingOnceExtractor(),
            new EndpointNormalizer(),
            new EndpointRepository(),
            null
        );

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(new HistoryItemPayload("https://app.example/a", "text/html", "<html>a</html>", 13));
        items.add(new HistoryItemPayload("https://app.example/b", "text/html", "<html>b</html>", 13));

        ScanResult result = scanService.scan(items, null, null);
        List<EndpointRecord> records = scanService.getRecords();

        assertTrue(result.getTotalItems() == 2, "totalItems mismatch");
        assertTrue(result.getProcessedItems() == 2, "processedItems mismatch");
        assertTrue(result.getErrorCount() == 1, "errorCount should be 1");
        assertTrue(result.getUniqueEndpoints() == 1, "uniqueEndpoints should be 1");
        assertTrue(records.size() == 1, "records size should be 1");
        assertTrue(records.get(0).getCount() == 1, "record count should be 1");
    }

    private static void testSampleDedupeCountFilterAndCsv() throws IOException {
        String sampleA = readFile("resources/sample_responses/m05_sample_a.html");
        String sampleB = readFile("resources/sample_responses/m05_sample_b.html");

        ScanService scanService = new ScanService(null);
        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(new HistoryItemPayload("https://app.example/page-a", "text/html", sampleA, sampleA.getBytes(StandardCharsets.UTF_8).length));
        items.add(new HistoryItemPayload("https://app.example/page-b", "text/html", sampleB, sampleB.getBytes(StandardCharsets.UTF_8).length));

        ScanResult result = scanService.scan(items, null, null);
        List<EndpointRecord> records = scanService.getRecords();

        assertTrue(result.getUniqueEndpoints() == 3, "uniqueEndpoints should be 3");
        Map<String, Integer> countByEndpoint = new HashMap<>();
        for (EndpointRecord record : records) {
            countByEndpoint.put(record.getEndpointUrl(), record.getCount());
        }

        assertTrue(countByEndpoint.get("https://api.example.com/v1/users") == 2, "users endpoint count should be 2");
        assertTrue(countByEndpoint.get("https://app.example/api/v1/orders") == 2, "orders endpoint count should be 2");
        assertTrue(countByEndpoint.get("https://app.example/api/v1/reports") == 1, "reports endpoint count should be 1");

        FilterService filterService = new FilterService();
        List<EndpointRecord> filtered = filterService.filter(records, "orders");
        assertTrue(filtered.size() == 1, "filtered size should be 1");
        assertTrue("https://app.example/api/v1/orders".equals(filtered.get(0).getEndpointUrl()), "filtered endpoint mismatch");

        Path csvFile = Files.createTempFile("endpoint-collector-m05-", ".csv");
        try {
            ExportService exportService = new ExportService();
            ExportService.ExportResult exportResult = exportService.exportCsv(csvFile.toString(), records);
            assertTrue(exportResult.isSuccess(), "csv export should succeed");

            List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
            assertTrue(lines.size() == 4, "csv line count should be 4");
            assertTrue("Endpoint,Host,SourceURL,Count".equals(lines.get(0)), "csv header mismatch");

            for (int i = 1; i < lines.size(); i++) {
                String[] columns = lines.get(i).split(",", -1);
                assertTrue(columns.length == 4, "csv column count should be 4");
            }
        } finally {
            Files.deleteIfExists(csvFile);
        }
    }

    private static void testAdvancedFilterSyntaxAndEndpointOnlyScope() {
        FilterService filterService = new FilterService();
        long now = System.currentTimeMillis();

        List<EndpointRecord> records = new ArrayList<>();
        records.add(
            new EndpointRecord(
                "https://cdn.example.com/assets/main.js",
                "cdn.example.com",
                "https://app.example.com/page-a",
                "GET",
                null,
                1,
                now,
                now
            )
        );
        records.add(
            new EndpointRecord(
                "https://cdn.example.com/assets/Agent.js",
                "cdn.example.com",
                "https://app.example.com/page-b",
                "GET",
                null,
                1,
                now,
                now
            )
        );
        records.add(
            new EndpointRecord(
                "https://api.example.com/v1/orders",
                "api.example.com",
                "https://app.example.com/Agent-dashboard",
                "GET",
                null,
                1,
                now,
                now
            )
        );

        List<EndpointRecord> endpointOnly = filterService.filter(records, "page-a");
        assertTrue(endpointOnly.isEmpty(), "search scope should be endpoint column only");

        List<EndpointRecord> globAndExclude = filterService.filter(records, "*.js !Agent", false);
        assertTrue(globAndExclude.size() == 1, "glob include/exclude filtering mismatch");
        assertTrue(
            "https://cdn.example.com/assets/main.js".equals(globAndExclude.get(0).getEndpointUrl()),
            "glob include/exclude endpoint mismatch"
        );

        List<EndpointRecord> regexAndExclude = filterService.filter(records, "\\.js$ !agent", true);
        assertTrue(regexAndExclude.size() == 1, "regex include/exclude filtering mismatch");
        assertTrue(
            "https://cdn.example.com/assets/main.js".equals(regexAndExclude.get(0).getEndpointUrl()),
            "regex include/exclude endpoint mismatch"
        );

        List<EndpointRecord> exclusionOnly = filterService.filter(records, "!orders", false);
        assertTrue(exclusionOnly.size() == 2, "exclusion-only filtering mismatch");
    }

    private static void testHtmlEntityDecoding() {
        ScanService scanService = new ScanService(null);
        String html = "<a href=\"/gn5/bbs/board.php?bo_table=qa&amp;sop=and&amp;sst=wr_hit&amp;sod=desc&amp;page=1\">qa</a>";

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/index.php",
                "text/html",
                html,
                html.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        List<EndpointRecord> records = scanService.getRecords();

        assertTrue(records.size() == 1, "html entity decoding unique endpoint mismatch");
        String endpoint = records.get(0).getEndpointUrl();
        assertTrue(endpoint.contains("&"), "decoded endpoint should contain '&'");
        assertTrue(!endpoint.contains("&amp;"), "decoded endpoint should not contain '&amp;'");
    }

    private static void testHtmlEntityDecodingWithoutSemicolon() {
        EndpointNormalizer normalizer = new EndpointNormalizer();
        String baseUrl = "https://target.example/index";

        EndpointCandidate slashEntityCandidate = new EndpointCandidate("/a&#47b", baseUrl, "text/html", "relative");
        String slashEntityEndpoint = normalizer.normalize(slashEntityCandidate, baseUrl);
        assertTrue(
            "https://target.example/a/b".equals(slashEntityEndpoint),
            "slash entity decoding failed: " + slashEntityEndpoint
        );

        EndpointCandidate ampEntityCandidate = new EndpointCandidate("/a?x=1&amp", baseUrl, "text/html", "relative");
        String ampEntityEndpoint = normalizer.normalize(ampEntityCandidate, baseUrl);
        assertTrue(ampEntityEndpoint != null && ampEntityEndpoint.contains("?x=1&"), "amp entity decoding failed");

        EndpointCandidate quoteEntityCandidate = new EndpointCandidate("/a?q=&#39,&#x27", baseUrl, "text/html", "relative");
        String quoteEntityEndpoint = normalizer.normalize(quoteEntityCandidate, baseUrl);
        assertTrue(quoteEntityEndpoint != null, "quote entity endpoint should not be null");
        assertTrue(!quoteEntityEndpoint.contains("&#39"), "decimal quote entity should be decoded");
        assertTrue(!quoteEntityEndpoint.contains("&#x27"), "hex quote entity should be decoded");
        assertTrue(quoteEntityEndpoint.indexOf('\'') >= 0, "decoded quote should exist");
    }

    private static void testDotRelativePathExtractionAndNormalization() {
        ScanService scanService = new ScanService(null);
        String html = "<img src=\"../../../assets/images/renew/banner_kakaopay.png\">";
        String sourceUrl = "https://target.example/lon/mbr/sub/tpl.html";

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                sourceUrl,
                "text/html",
                html,
                html.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/assets/images/renew/banner_kakaopay.png"),
            "dot-relative path extraction missing: " + endpoints
        );
    }

    private static void testDotRelativePathAboveRootClamping() {
        ScanService scanService = new ScanService(null);
        String javascript = "var icon='../../../assets/images/front/icons/icon_arrow_pop_next.png';";
        String sourceUrl = "https://target.example/static/app.js";

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                sourceUrl,
                "application/javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/assets/images/front/icons/icon_arrow_pop_next.png"),
            "dot-relative above-root clamping missing: " + endpoints
        );
    }

    private static void testDynamicJavaScriptRoutingAndFrameworkSignatures() {
        ScanService scanService = new ScanService(null);
        String javascript = ""
            + "const apiBase = '/api/v1';\n"
            + "fetch(`${apiBase}/users/${userId}`);\n"
            + "axios.get(apiBase + '/reports');\n"
            + "const xhr = new XMLHttpRequest(); xhr.open('GET', `/orders/${orderId}`);\n"
            + "app.get('/express/:id', handler);\n"
            + "router.post(`/router/${postId}`, handler);\n"
            + "fastify.route({ method: 'GET', url: '/fastify/:itemId', handler });\n";

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/index.js",
                "application/javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/api/v1/users/{var}"),
            "template literal route missing: " + endpoints
        );
        assertTrue(endpoints.contains("https://target.example/api/v1/reports"), "string concatenation route missing");
        assertTrue(endpoints.contains("https://target.example/orders/{var}"), "xhr template route missing");
        assertTrue(endpoints.contains("https://target.example/express/{id}"), "express signature route missing");
        assertTrue(endpoints.contains("https://target.example/router/{var}"), "router signature route missing");
        assertTrue(endpoints.contains("https://target.example/fastify/{itemId}"), "fastify route signature missing");
    }

    private static void testLargeJavascriptPayloadExtraction() {
        ScanService scanService = new ScanService(null);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 220_000; i++) {
            builder.append("var a").append(i).append("=1;");
        }
        builder.append("window.location.href='/lp/mcunl/pswrChng';");
        String javascript = builder.toString();

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/static/app.js",
                "application/x-javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/lp/mcunl/pswrChng"),
            "large javascript endpoint missing"
        );
    }

    private static void testWorkspaceAppJsSampleIfPresent() throws IOException {
        Path appJsPath = Path.of("app.js");
        if (!Files.exists(appJsPath)) {
            return;
        }

        String javascript = Files.readString(appJsPath, StandardCharsets.UTF_8);
        ScanService scanService = new ScanService(null);
        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/static/app.js",
                "application/javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/lp/mcunl/pswrChng"),
            "workspace app.js endpoint missing"
        );
        List<String> iconRelated = endpoints.stream()
            .filter(value -> value.contains("icon_arrow_pop_next") || value.contains("assets/images/front/icons"))
            .sorted()
            .collect(Collectors.toList());
        assertTrue(
            endpoints.contains("https://target.example/assets/images/front/icons/icon_arrow_pop_next.png"),
            "workspace app.js dot-relative asset endpoint missing: " + iconRelated
        );
    }

    private static void testWorkspaceAppJsRegexRangeCoverageIfPresent() throws IOException {
        Path appJsPath = Path.of("app.js");
        if (!Files.exists(appJsPath)) {
            return;
        }
        String javascript = Files.readString(appJsPath, StandardCharsets.UTF_8);
        int targetIndex = javascript.indexOf("../../../assets/images/front/icons/icon_arrow_pop_next.png");
        if (targetIndex < 0) {
            return;
        }

        JsAstEndpointExtractor astExtractor = new JsAstEndpointExtractor();
        List<JsAstEndpointExtractor.SourceRange> ranges = astExtractor.extractRegexLiteralRanges(javascript);
        boolean covered = false;
        for (JsAstEndpointExtractor.SourceRange range : ranges) {
            if (range != null && range.contains(targetIndex)) {
                covered = true;
                break;
            }
        }
        assertTrue(!covered, "icon path offset should not be inside regex literal range");
    }

    private static void testJavaScriptDivisionFalsePositiveSuppression() {
        ScanService scanService = new ScanService(null);
        String javascript = ""
            + "var m1=10*Math.floor(parseInt(pramt*inrt*Math.pow(1+inrt,tr)/(Math.pow(1+inrt,tr)-1))/10),"
            + "m2=10*Math.floor(parseInt(pramt*inrt*Math.pow(1+inrt,tr)/(Math.pow(1+inrt,tr)-1))/10),"
            + "m3=10*Math.floor(parseInt(pramt/tr+pramt*inrt)/10);"
            + "window.location.href='/lp/mcunl/pswrChng';";

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/static/app.js",
                "application/javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/lp/mcunl/pswrChng"),
            "expected endpoint missing"
        );
        for (String endpoint : endpoints) {
            assertTrue(!endpoint.contains("/10),m2="), "division expression false positive detected: " + endpoint);
        }
    }

    private static void testRegexLiteralFalsePositiveSuppression() {
        ScanService scanService = new ScanService(null);
        String javascript = ""
            + "var edge = /(edge)\\/([\\d.]+)/i.test(navigator.userAgent);"
            + "var sanitized = value.replace(/-/gi, '');"
            + "var month = date.match(/MM/);"
            + "var day = date.match(/DD/);"
            + "var anchored = /#.*$/;"
            + "var tail = /:$/;"
            + "var brace = /}$/};"
            + "var messy = /.*/}};"
            + "var maybe = /.?&/;"
            + "var open1 = /_("
            + "x;"
            + "var open2 = /-("
            + "x;"
            + "var flag = /g;"
            + "window.location.href='/lp/mcunl/pswrChng';";

        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/static/external.pre.js",
                "application/javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/lp/mcunl/pswrChng"),
            "expected endpoint missing"
        );
        for (String endpoint : endpoints) {
            assertTrue(!endpoint.endsWith("/g"), "regex flag false positive detected: " + endpoint);
            assertTrue(!endpoint.endsWith("/gi"), "regex flag false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/i.test"), "regex test false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/(edge"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/#.*$/"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/:$/"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/_("), "regex fragment false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/-("), "regex fragment false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/MM/"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/DD/"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/}$/}"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/.*/}}"), "regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/.?&"), "regex literal false positive detected: " + endpoint);
        }
    }

    private static void testWorkspaceExternalPreSampleIfPresent() throws IOException {
        Path samplePath = Path.of("external.pre.js");
        if (!Files.exists(samplePath)) {
            return;
        }

        String javascript = Files.readString(samplePath, StandardCharsets.UTF_8);
        ScanService scanService = new ScanService(null);
        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/static/external.pre.js",
                "application/javascript",
                javascript,
                javascript.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        for (String endpoint : endpoints) {
            assertTrue(!endpoint.endsWith("/g"), "workspace regex flag false positive detected: " + endpoint);
            assertTrue(!endpoint.endsWith("/gi"), "workspace regex flag false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/i.test"), "workspace regex test false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/(edge"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/#.*$/"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/:$/"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/_("), "workspace regex fragment false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/-("), "workspace regex fragment false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/MM/"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/DD/"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/}$/}"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/.*/}}"), "workspace regex literal false positive detected: " + endpoint);
            assertTrue(!endpoint.contains("/.?&"), "workspace regex literal false positive detected: " + endpoint);
        }
    }

    private static void testWorkspaceTplSampleIfPresent() throws IOException {
        Path samplePath = Path.of("tpl.html");
        if (!Files.exists(samplePath)) {
            return;
        }

        String body = Files.readString(samplePath, StandardCharsets.UTF_8);
        ScanService scanService = new ScanService(null);
        List<HistoryItemPayload> items = new ArrayList<>();
        items.add(
            new HistoryItemPayload(
                "https://target.example/lon/mbr/sub/tpl.html",
                "text/html",
                body,
                body.getBytes(StandardCharsets.UTF_8).length
            )
        );

        scanService.scan(items, null, null);
        Set<String> endpoints = scanService.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());

        assertTrue(
            endpoints.contains("https://target.example/assets/images/renew/banner_kakaopay.png"),
            "workspace tpl dot-relative path missing"
        );
    }

    private static void testWorkspaceRequestJsonSampleIfPresent() throws IOException {
        Path samplePath = Path.of("request.json");
        if (!Files.exists(samplePath)) {
            return;
        }

        String body = Files.readString(samplePath, StandardCharsets.UTF_8);

        ScanService scanServiceWithJsonType = new ScanService(null);
        List<HistoryItemPayload> jsonTypeItems = new ArrayList<>();
        jsonTypeItems.add(
            new HistoryItemPayload(
                "https://target.example/api/request",
                "application/json",
                body,
                body.getBytes(StandardCharsets.UTF_8).length
            )
        );
        scanServiceWithJsonType.scan(jsonTypeItems, null, null);
        Set<String> jsonTypeEndpoints = scanServiceWithJsonType.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());
        assertTrue(
            jsonTypeEndpoints.contains("https://target.example/contents/GDS_LP_IMG_LIST/20210706_eaab077f2d0d1b87e61c52570d3494d0b42c0831.jpg"),
            "request.json endpoint missing when content-type=application/json"
        );

        ScanService scanServiceWithoutType = new ScanService(null);
        List<HistoryItemPayload> emptyTypeItems = new ArrayList<>();
        emptyTypeItems.add(
            new HistoryItemPayload(
                "https://target.example/api/request",
                "",
                body,
                body.getBytes(StandardCharsets.UTF_8).length
            )
        );
        scanServiceWithoutType.scan(emptyTypeItems, null, null);
        Set<String> emptyTypeEndpoints = scanServiceWithoutType.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());
        assertTrue(
            emptyTypeEndpoints.contains("https://target.example/contents/GDS_LP_IMG_LIST/20210706_eaab077f2d0d1b87e61c52570d3494d0b42c0831.jpg"),
            "request.json endpoint missing when content-type is empty"
        );

        ScanService scanServiceWithUnknownType = new ScanService(null);
        List<HistoryItemPayload> unknownTypeItems = new ArrayList<>();
        unknownTypeItems.add(
            new HistoryItemPayload(
                "https://target.example/api/request",
                "application/octet-stream",
                body,
                body.getBytes(StandardCharsets.UTF_8).length
            )
        );
        scanServiceWithUnknownType.scan(unknownTypeItems, null, null);
        Set<String> unknownTypeEndpoints = scanServiceWithUnknownType.getRecords().stream()
            .map(EndpointRecord::getEndpointUrl)
            .collect(Collectors.toSet());
        assertTrue(
            unknownTypeEndpoints.contains("https://target.example/contents/GDS_LP_IMG_LIST/20210706_eaab077f2d0d1b87e61c52570d3494d0b42c0831.jpg"),
            "request.json endpoint missing when content-type is unknown"
        );
    }

    private static String readFile(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static class ThrowingOnceExtractor extends EndpointExtractor {
        private int callCount = 0;

        @Override
        public List<EndpointCandidate> extract(String responseText, String contentType, String sourceUrl) {
            callCount += 1;
            if (callCount == 1) {
                throw new IllegalStateException("forced parse failure");
            }
            List<EndpointCandidate> candidates = new ArrayList<>();
            candidates.add(new EndpointCandidate("https://stable.example/api", sourceUrl, contentType, "absolute"));
            return candidates;
        }
    }
}
