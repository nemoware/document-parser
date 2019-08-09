package com.nemo.document.parser.web;

import com.nemo.document.parser.DocumentType;

public class DocumentParserRequest {
    private String base64Content;
    private DocumentType documentType;

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }
}
