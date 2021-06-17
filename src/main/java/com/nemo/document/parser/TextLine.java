package com.nemo.document.parser;

import org.apache.pdfbox.text.TextPosition;

import java.util.List;

public class TextLine {
    private List<TextPosition> textPositions = null;
    private String text = "";

    public List<TextPosition> getTextPositions() {
        return textPositions;
    }

    public void setTextPositions(List<TextPosition> textPositions) {
        this.textPositions = textPositions;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
