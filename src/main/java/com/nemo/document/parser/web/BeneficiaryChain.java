package com.nemo.document.parser.web;

import com.nemo.document.parser.Beneficiary;

import java.util.ArrayList;
import java.util.List;

public class BeneficiaryChain {
    private List<Beneficiary> benefeciaries = new ArrayList<>();
    private String version;

    public List<Beneficiary> getBenefeciaries() {
        return benefeciaries;
    }

    public void setBenefeciaries(List<Beneficiary> benefeciaries) {
        this.benefeciaries = benefeciaries;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
