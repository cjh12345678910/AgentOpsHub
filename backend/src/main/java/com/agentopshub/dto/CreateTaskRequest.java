package com.agentopshub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class CreateTaskRequest {
    @NotBlank(message = "prompt is required")
    private String prompt;

    @NotBlank(message = "outputFormat is required")
    @Pattern(regexp = "markdown|json|both", message = "outputFormat must be markdown, json, or both")
    private String outputFormat;

    private List<String> docIds;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public List<String> getDocIds() { return docIds; }
    public void setDocIds(List<String> docIds) { this.docIds = docIds; }
}
