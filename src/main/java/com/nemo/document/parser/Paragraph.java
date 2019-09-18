package com.nemo.document.parser;

public class Paragraph {
    private TextSegment paragraphHeader = new TextSegment(-1, "");
    private TextSegment paragraphBody = new TextSegment(-1, "");

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
