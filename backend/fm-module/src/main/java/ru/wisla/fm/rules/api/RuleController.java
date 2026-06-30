package ru.wisla.fm.rules.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<ProcessingRuleDto> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String approvalStatus
    ) {
        return ruleService.listRules(ruleType, enabled, approvalStatus);
    }

    @PostMapping
    public ResponseEntity<ProcessingRuleDetailDto> createRule(@Valid @RequestBody ProcessingRuleCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.createRule(request));
    }

    @GetMapping("/{id}")
    public ProcessingRuleDetailDto getRule(@PathVariable UUID id) {
        return ruleService.getRule(id);
    }

    @PatchMapping("/{id}")
    public ProcessingRuleDetailDto patchRule(@PathVariable UUID id, @RequestBody ProcessingRulePatch patch) {
        return ruleService.patchRule(id, patch);
    }
}
