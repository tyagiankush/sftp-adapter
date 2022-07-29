package com.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RequestEntity implements Serializable, IsEmpty {
    @JsonProperty(value = "requestMetadata")
    public RequestMetadata requestMetadata;
    @JsonProperty(value = "requestBody")
    public Object requestJson;
    @JsonProperty(value = "requestHeaders")
    public Object requestHeaders;

    public RequestEntity() {
        super();
    }

    public Object getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(Object requestJson) {
        this.requestJson = requestJson;
    }

    public RequestMetadata getRequestMetadata() {
        return requestMetadata;
    }

    public void setRequestMetadata(RequestMetadata requestMetadata) {
        this.requestMetadata = requestMetadata;
    }

    public Object getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Object requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    @Override
    public String toString() {
        return "RequestEntity{" +
                "requestMetadata=" + requestMetadata +
                ", requestJson=" + requestJson +
                ", requestHeaders=" + requestHeaders +
                '}';
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
