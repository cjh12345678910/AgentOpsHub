package com.agentopshub.dto;

import java.util.List;

public class DlqTaskListResponse {
    private List<DlqTaskItemResponse> items;

    public DlqTaskListResponse() {
    }

    public DlqTaskListResponse(List<DlqTaskItemResponse> items) {
        this.items = items;
    }

    public List<DlqTaskItemResponse> getItems() {
        return items;
    }

    public void setItems(List<DlqTaskItemResponse> items) {
        this.items = items;
    }
}
