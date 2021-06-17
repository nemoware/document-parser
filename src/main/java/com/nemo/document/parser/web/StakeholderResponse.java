package com.nemo.document.parser.web;

import com.nemo.document.parser.Stakeholder;

import java.util.ArrayList;
import java.util.List;

public class StakeholderResponse {
    private List<Stakeholder> stakeholders = new ArrayList<>();
    private String version;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<Stakeholder> getStakeholders() {
        return stakeholders;
    }

    public void setStakeholders(List<Stakeholder> stakeholders) {
        this.stakeholders = stakeholders;
    }
}
