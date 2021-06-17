package com.nemo.document.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Stakeholder {
    private String name;
    private String shortName;
    private List<Reason> reasons = new ArrayList<>();
    private BigDecimal share = new BigDecimal(0);

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public List<Reason> getReasons() {
        return reasons;
    }

    public void setReasons(List<Reason> reasons) {
        this.reasons = reasons;
    }

    public BigDecimal getShare() {
        return share;
    }

    public void setShare(BigDecimal share) {
        this.share = share;
    }
}
