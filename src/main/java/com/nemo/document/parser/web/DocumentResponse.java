package com.nemo.document.parser.web;

public class DocumentResponse {
    private String base64Document;

    public String getBase64Document() {
        return base64Document;
    }

    public void setBase64Document(String base64Document) {
        this.base64Document = base64Document;
    }

    public DocumentResponse(String base64Document) {
        this.base64Document = base64Document;
    }
}
