package com.nemo.document.parser;

public class TextSegment {
    private int offset;
    private StringBuilder text;

    public TextSegment(int startSymbol, String text) {
        this.offset = startSymbol;
        this.text = new StringBuilder(text);
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return text.length();
    }

    public String getText() {
        return text.toString();
    }

    public void addText(String text){
        if(this.text.length() > 0){
            this.text.append(System.lineSeparator());
        }
        this.text.append(text);
    }
}
