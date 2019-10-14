package com.nemo.document.parser;

import java.util.ArrayList;
import java.util.List;

public class MultiDocumentStructure {
    private List<DocumentStructure> documents = new ArrayList<>(1);
    private String version;

    public List<DocumentStructure> getDocuments() {
        return documents;
    }

    public void addDocument(DocumentStructure document){
        this.documents.add(document);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
