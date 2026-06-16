package com.investmenttracker.service;

import java.util.Map;

public class NotFoundException extends RuntimeException {

    private final String resource;
    private final Object id;

    public NotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
        this.resource = resource;
        this.id = id;
    }

    public String getResource() {
        return resource;
    }

    public Object getId() {
        return id;
    }
}
