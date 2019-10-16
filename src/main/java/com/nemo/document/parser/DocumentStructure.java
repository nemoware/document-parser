package com.nemo.document.parser;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DocumentStructure {
    @JsonSerialize(using = LocalDateSerializer.class)
    private LocalDate documentDate;
    private DocumentType documentType = DocumentType.UNKNOWN;
    private String documentNumber = "";
    private TextSegment documentDateSegment;
    private TextSegment documentNumberSegment;

    private List<Paragraph> paragraphs = new ArrayList<>();

    public List<Paragraph> getParagraphs() {
        return paragraphs;
    }

    public List<Paragraph> addParagraph(Paragraph paragraph){
        paragraphs.add(paragraph);
        return paragraphs;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public TextSegment getDocumentDateSegment() {
        return documentDateSegment;
    }

    public void setDocumentDateSegment(TextSegment documentDateSegment) {
        this.documentDateSegment = documentDateSegment;
    }

    public TextSegment getDocumentNumberSegment() {
        return documentNumberSegment;
    }

    public void setDocumentNumberSegment(TextSegment documentNumberSegment) {
        this.documentNumberSegment = documentNumberSegment;
    }
}
