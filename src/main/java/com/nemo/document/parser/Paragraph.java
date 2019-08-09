package com.nemo.document.parser;

public class Paragraph {
    private TextSegment paragraphHeader;
    private TextSegment paragraphBody;

    public TextSegment getParagraphHeader() {
        return paragraphHeader;
    }

    public void setParagraphHeader(TextSegment paragraphHeader) {
        this.paragraphHeader = paragraphHeader;
    }

    public TextSegment getParagraphBody() {
        return paragraphBody;
    }

    public void setParagraphBody(TextSegment paragraphBody) {
        this.paragraphBody = paragraphBody;
    }
}
