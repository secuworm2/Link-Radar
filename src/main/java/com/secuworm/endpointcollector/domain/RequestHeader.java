package com.secuworm.endpointcollector.domain;

public class RequestHeader {
    private final String name;
    private final String value;

    public RequestHeader(String name, String value) {
        this.name = name == null ? "" : name;
        this.value = value == null ? "" : value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
