package com.nemo.document.parser.web;

import com.nemo.document.parser.DocumentParser;

public class StatusResponse {
    private String status = "ok";
    private String version = DocumentParser.getVersion();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
