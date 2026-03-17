package com.agentopshub.controller;

import com.agentopshub.dto.ToolAuditItemResponse;
import com.agentopshub.dto.ToolCatalogItemResponse;
import com.agentopshub.dto.ToolPermissionResponse;
import com.agentopshub.dto.ToolPermissionUpdateRequest;
import com.agentopshub.service.ToolGovernanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolGovernanceController {
    private final ToolGovernanceService toolGovernanceService;

    public ToolGovernanceController(ToolGovernanceService toolGovernanceService) {
        this.toolGovernanceService = toolGovernanceService;
    }

    @GetMapping("/catalog")
    public List<ToolCatalogItemResponse> listCatalog(@RequestParam(required = false) String status) {
        return toolGovernanceService.listToolCatalog(status);
    }

    @GetMapping("/permissions")
    public List<ToolPermissionResponse> listPermissions() {
        return toolGovernanceService.listPermissions();
    }

    @GetMapping("/permissions/{role}")
    public ToolPermissionResponse getPermissionByRole(@PathVariable String role) {
        return toolGovernanceService.getPermissionByRole(role);
    }

    @PostMapping("/permissions")
    public ToolPermissionResponse updatePermissions(@Valid @RequestBody ToolPermissionUpdateRequest request) {
        return toolGovernanceService.updatePermissions(request);
    }

    @PutMapping("/permissions/{role}")
    public ToolPermissionResponse putPermissions(@PathVariable String role, @Valid @RequestBody ToolPermissionUpdateRequest request) {
        return toolGovernanceService.putPermissions(role, request.getScopes());
    }

    @DeleteMapping("/permissions/{role}")
    public void deletePermission(@PathVariable String role) {
        toolGovernanceService.deletePermission(role);
    }

    @GetMapping("/audit")
    public List<ToolAuditItemResponse> listAudit(
        @RequestParam(required = false) String policyDecision,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Integer limit
    ) {
        return toolGovernanceService.listAudit(policyDecision, from, to, limit);
    }
}
