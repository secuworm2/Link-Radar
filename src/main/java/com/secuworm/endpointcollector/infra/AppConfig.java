package com.secuworm.endpointcollector.infra;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppConfig {
    public static final int SCAN_BATCH_SIZE = 100;
    public static final String EXPORT_DEFAULT_FILENAME = "endpoints.csv";
    public static final Set<String> SUPPORTED_CONTENT_TYPES = createSupportedContentTypes();

    private AppConfig() {
    }

    private static Set<String> createSupportedContentTypes() {
        Set<String> types = new HashSet<>();
        types.add("text/html");
        types.add("application/json");
        types.add("application/javascript");
        types.add("application/x-javascript");
        types.add("text/javascript");
        types.add("text/plain");
        return Collections.unmodifiableSet(types);
    }
}
