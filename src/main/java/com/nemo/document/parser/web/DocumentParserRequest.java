package com.nemo.document.parser.web;

import com.nemo.document.parser.DocumentFileType;

public class DocumentParserRequest {
    private String base64Content;
    private DocumentFileType documentFileType;

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
    }

    public DocumentFileType getDocumentFileType() {
        return documentFileType;
    }

    public void setDocumentFileType(DocumentFileType documentFileType) {
        this.documentFileType = documentFileType;
    }
}
