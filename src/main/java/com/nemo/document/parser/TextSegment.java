package com.nemo.document.parser;

public class TextSegment {
    private int offset;
    private StringBuilder text;
    private int length = 0;

    public TextSegment(int startSymbol, String text) {
        this.offset = startSymbol;
        this.text = new StringBuilder(text);
        this.length = text.length();
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getText() {
        return text.toString();
    }

    public void addText(String text){
        if(this.text.length() > 0 && this.text.lastIndexOf(System.lineSeparator()) != this.text.length() - System.lineSeparator().length()){
            this.text.append(System.lineSeparator());
            this.length += System.lineSeparator().length();
        }
        this.text.append(text);
        this.length += text.length();
    }
}
