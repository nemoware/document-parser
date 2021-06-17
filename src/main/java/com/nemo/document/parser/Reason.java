package com.nemo.document.parser;

import java.time.LocalDate;
import java.util.Date;

public class Reason {
    private String text;
    private LocalDate date;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}
