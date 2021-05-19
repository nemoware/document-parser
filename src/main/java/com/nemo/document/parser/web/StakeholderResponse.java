package com.nemo.document.parser.web;

import com.nemo.document.parser.StakeholderList;

import java.util.ArrayList;
import java.util.List;

public class StakeholderResponse {
    private List<StakeholderList> sheets = new ArrayList<>();
    private String version;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<StakeholderList> getSheets() {
        return sheets;
    }

    public void setSheets(List<StakeholderList> sheets) {
        this.sheets = sheets;
    }
}
