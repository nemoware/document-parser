package com.nemo.document.parser;

public class RiskMatrixRow {
    String violation;
    String subject;
    String risk;
    String recommendation;
    String disadvantage;

    public String getViolation() {
        return violation;
    }

    public void setViolation(String violation) {
        this.violation = violation;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getDisadvantage() {
        return disadvantage;
    }

    public void setDisadvantage(String disadvantage) {
        this.disadvantage = disadvantage;
    }
}
