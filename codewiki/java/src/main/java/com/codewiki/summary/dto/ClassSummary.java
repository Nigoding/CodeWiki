package com.codewiki.summary.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ClassSummary {

    @JsonPropertyDescription("The role the class plays in the repository")
    private String role;

    @JsonPropertyDescription("Main functions and capabilities of the class")
    private String keyFunctionality;

    @JsonPropertyDescription("What the class aims to achieve")
    private String purpose;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getKeyFunctionality() {
        return keyFunctionality;
    }

    public void setKeyFunctionality(String keyFunctionality) {
        this.keyFunctionality = keyFunctionality;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
