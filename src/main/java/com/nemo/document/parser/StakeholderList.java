package com.nemo.document.parser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StakeholderList {
    private LocalDate date;
    private List<Stakeholder> stakeholders = new ArrayList<>();

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<Stakeholder> getStakeholders() {
        return stakeholders;
    }

    public void setStakeholders(List<Stakeholder> stakeholders) {
        this.stakeholders = stakeholders;
    }
}
