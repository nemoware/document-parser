package com.nemo.document.parser;

public class ListNumber {
    private ListNumber subNumber = null;
    private int level = 0;
    private String format;
    private int number = 0;

    public ListNumber(int level, String format) {
        this.level = level;
        this.format = format;
    }

    public ListNumber getSubNumber() {
        return subNumber;
    }

    public void setSubNumber(ListNumber subNumber) {
        this.subNumber = subNumber;
    }

    public int getLevel() {
        return level;
    }

    public String getFormat() {
        return format;
    }

    public int getNumber() {
        return number;
    }

    public int incrementNumber(){
        return ++number;
    }

    public void overrideNumber(int newNumber){
        number = newNumber;
    }
}
