package com.nemo.document.parser;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.Map;

public class ConclusionRequest {
    String base64Logo;
    String subsidiaryName;
    Date auditDate;
    RiskMatrixRow[] riskMatrix;
    String[] orgLevels;
    Violation[] violations;

    public String getBase64Logo() {
        return base64Logo;
    }

    public void setBase64Logo(String base64Logo) {
        this.base64Logo = base64Logo;
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

    public RiskMatrixRow[] getRiskMatrix() {
        return riskMatrix;
    }

    public void setRiskMatrix(RiskMatrixRow[] riskMatrix) {
        this.riskMatrix = riskMatrix;
    }

    public String[] getOrgLevels() {
        return orgLevels;
    }

    public void setOrgLevels(String[] orgLevels) {
        this.orgLevels = orgLevels;
    }

    public Violation[] getViolations() {
        return violations;
    }

    public void setViolations(Violation[] violations) {
        this.violations = violations;
    }
}
