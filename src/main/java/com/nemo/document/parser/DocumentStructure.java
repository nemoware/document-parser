package com.nemo.document.parser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DocumentStructure {
    private LocalDate documentDate;

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
}
