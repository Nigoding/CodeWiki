package com.codewiki.summary.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class MethodSummary {

    @JsonPropertyDescription("The exact name of the function.")
    private String functionName;

    @JsonPropertyDescription("List of parameters with their types. Describe them in business terms if possible (e.g., 'Order ID' instead of 'Long id').")
    private List<String> inputs;

    @JsonPropertyDescription("Return type and value, explaining its business significance.")
    private String outputs;

    @JsonPropertyDescription("Context-aware purpose. **CRITICAL**: Start with the business goal derived from the Package Context, then mention the technical action.")
    private String purpose;

    @JsonPropertyDescription("Step-by-step logic outline, using terminology from the Package/Class Context.")
    private List<String> workflow;

    @JsonPropertyDescription("Textual description of object interactions (sequence diagram style). Highlight dependencies on other services mentioned in the context.")
    private String sequenceDiagram;

    @JsonPropertyDescription("Data processing steps: Input -> Transformation (Business Logic) -> Output.")
    private String dataFlow;

    @JsonPropertyDescription("Potential side effects (e.g., DB updates, external API calls, cache eviction).")
    private List<String> sideEffects;

    @JsonPropertyDescription("A concise paragraph synthesizing the technical logic with the package's business intent.")
    private String finalSummary;

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public List<String> getInputs() {
        return inputs;
    }

    public void setInputs(List<String> inputs) {
        this.inputs = inputs;
    }

    public String getOutputs() {
        return outputs;
    }

    public void setOutputs(String outputs) {
        this.outputs = outputs;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public List<String> getWorkflow() {
        return workflow;
    }

    public void setWorkflow(List<String> workflow) {
        this.workflow = workflow;
    }

    public String getSequenceDiagram() {
        return sequenceDiagram;
    }

    public void setSequenceDiagram(String sequenceDiagram) {
        this.sequenceDiagram = sequenceDiagram;
    }

    public String getDataFlow() {
        return dataFlow;
    }

    public void setDataFlow(String dataFlow) {
        this.dataFlow = dataFlow;
    }

    public List<String> getSideEffects() {
        return sideEffects;
    }

    public void setSideEffects(List<String> sideEffects) {
        this.sideEffects = sideEffects;
    }

    public String getFinalSummary() {
        return finalSummary;
    }

    public void setFinalSummary(String finalSummary) {
        this.finalSummary = finalSummary;
    }
}
