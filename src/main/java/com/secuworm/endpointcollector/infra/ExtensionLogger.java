package com.secuworm.endpointcollector.infra;

import burp.api.montoya.MontoyaApi;

public class ExtensionLogger {
    private final MontoyaApi api;
    private final String prefix;

    public ExtensionLogger(MontoyaApi api, String prefix) {
        this.api = api;
        this.prefix = prefix;
    }

    public void info(String message) {
        if (api == null) {
            return;
        }
        api.logging().logToOutput(prefix + " " + message);
    }

    public void error(String message) {
        if (api == null) {
            return;
        }
        api.logging().logToError(prefix + " " + message);
    }
}
