package com.nemo.document.parser;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DocumentStructure {
//    @JsonSerialize(using = LocalDateSerializer.class)
    private DocumentType documentType = DocumentType.UNKNOWN;

    private List<Paragraph> paragraphs = new ArrayList<>();

    public List<Paragraph> getParagraphs() {
        return paragraphs;
    }

    public List<Paragraph> addParagraph(Paragraph paragraph){
        paragraphs.add(paragraph);
        return paragraphs;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }
}
