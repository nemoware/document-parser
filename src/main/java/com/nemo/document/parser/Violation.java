package com.nemo.document.parser;

public class Violation {
    String foundingDocument;
    String reference;
    String violationType;
    String violationReason;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getViolationType() {
        return violationType;
    }

    public void setViolationType(String violationType) {
        this.violationType = violationType;
    }

    public String getViolationReason() {
        return violationReason;
    }

    public void setViolationReason(String violationReason) {
        this.violationReason = violationReason;
    }

    public String getFoundingDocument() {
        return foundingDocument;
    }

    public void setFoundingDocument(String foundingDocument) {
        this.foundingDocument = foundingDocument;
    }
}
