package com.nemo.document.parser;

import java.util.ArrayList;
import java.util.List;

public class Reason {
    private String text;
    private List<String> persons = new ArrayList<>();

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getPersons() {
        return persons;
    }

    public void setPersons(List<String> persons) {
        this.persons = persons;
    }
}
