package com.nemo.document.parser;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.Map;

public class ConclusionRequest {
    String base64Template;
    String subsidiaryName;
    Date auditDate;
//    RiskMatrixRow[] riskMatrix;
//    String[] orgLevels;
    Violation[] violations;
    Date auditStart;
    Date auditEnd;
    String intro = "";
    String shortSummary = "";
    String corporateStructure1 = "";
    String corporateStructure2 = "";
    String results1 = "";
    String results2 = "";
    String results3 = "";
    String results4 = "";
    String strengths = "";
    String disadvantages = "";
    String risks = "";
    String recommendations = "";
    Subdivision[] subdivisions;
    String legalEntityType = "";

    public String getBase64Template() {
        return base64Template;
    }

    public void setBase64Template(String base64Template) {
        this.base64Template = base64Template;
    }

    public String getSubsidiaryName() {
        return subsidiaryName;
    }

    public void setSubsidiaryName(String subsidiaryName) {
        this.subsidiaryName = subsidiaryName;
    }

    public Date getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(Date auditDate) {
        this.auditDate = auditDate;
    }

    public Violation[] getViolations() {
        return violations;
    }

    public void setViolations(Violation[] violations) {
        this.violations = violations;
    }

    public Date getAuditStart() {
        return auditStart;
    }

    public void setAuditStart(Date auditStart) {
        this.auditStart = auditStart;
    }

    public Date getAuditEnd() {
        return auditEnd;
    }

    public void setAuditEnd(Date auditEnd) {
        this.auditEnd = auditEnd;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getShortSummary() {
        return shortSummary;
    }

    public void setShortSummary(String shortSummary) {
        this.shortSummary = shortSummary;
    }

    public String getCorporateStructure1() {
        return corporateStructure1;
    }

    public void setCorporateStructure1(String corporateStructure1) {
        this.corporateStructure1 = corporateStructure1;
    }

    public String getCorporateStructure2() {
        return corporateStructure2;
    }

    public void setCorporateStructure2(String corporateStructure2) {
        this.corporateStructure2 = corporateStructure2;
    }

    public String getResults1() {
        return results1;
    }

    public void setResults1(String results1) {
        this.results1 = results1;
    }

    public String getResults2() {
        return results2;
    }

    public void setResults2(String results2) {
        this.results2 = results2;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getDisadvantages() {
        return disadvantages;
    }

    public void setDisadvantages(String disadvantages) {
        this.disadvantages = disadvantages;
    }

    public String getRisks() {
        return risks;
    }

    public void setRisks(String risks) {
        this.risks = risks;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public Subdivision[] getSubdivisions() {
        return subdivisions;
    }

    public void setSubdivisions(Subdivision[] subdivisions) {
        this.subdivisions = subdivisions;
    }

    public String getResults3() {
        return results3;
    }

    public void setResults3(String results3) {
        this.results3 = results3;
    }

    public String getResults4() {
        return results4;
    }

    public void setResults4(String results4) {
        this.results4 = results4;
    }

    public String getLegalEntityType() {
        return legalEntityType;
    }

    public void setLegalEntityType(String legalEntityType) {
        this.legalEntityType = legalEntityType;
    }
}
