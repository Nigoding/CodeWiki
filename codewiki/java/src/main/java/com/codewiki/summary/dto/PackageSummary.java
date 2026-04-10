package com.codewiki.summary.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class PackageSummary {

    @JsonPropertyDescription("A concise statement of the business value. Do not use this description as the key.")
    private String coreBusinessFunction;

    @JsonPropertyDescription("List of business processes derived from the code.")
    private List<BusinessFlow> businessFlows;

    @JsonPropertyDescription("List of key business entities managed by this package.")
    private List<String> keyBusinessEntities;

    public String getCoreBusinessFunction() {
        return coreBusinessFunction;
    }

    public void setCoreBusinessFunction(String coreBusinessFunction) {
        this.coreBusinessFunction = coreBusinessFunction;
    }

    public List<BusinessFlow> getBusinessFlows() {
        return businessFlows;
    }

    public void setBusinessFlows(List<BusinessFlow> businessFlows) {
        this.businessFlows = businessFlows;
    }

    public List<String> getKeyBusinessEntities() {
        return keyBusinessEntities;
    }

    public void setKeyBusinessEntities(List<String> keyBusinessEntities) {
        this.keyBusinessEntities = keyBusinessEntities;
    }

    public static class BusinessFlow {
        @JsonPropertyDescription("Name of the process, e.g., 'Loan Approval'")
        private String flowName;

        @JsonPropertyDescription("List of steps in the flow")
        private List<String> steps;

        @JsonPropertyDescription("Brief explanation of this flow.")
        private String description;

        public String getFlowName() {
            return flowName;
        }

        public void setFlowName(String flowName) {
            this.flowName = flowName;
        }

        public List<String> getSteps() {
            return steps;
        }

        public void setSteps(List<String> steps) {
            this.steps = steps;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
