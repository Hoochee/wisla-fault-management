package ru.wisla.fm.rules.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;

import java.util.List;
import java.util.UUID;

@Service
public class RuleService {

    private final ProcessingRuleRepository processingRuleRepository;
    private final ObjectMapper objectMapper;
    private final RuleCanvasValidator ruleCanvasValidator;

    public RuleService(ProcessingRuleRepository processingRuleRepository,
                       ObjectMapper objectMapper,
                       RuleCanvasValidator ruleCanvasValidator) {
        this.processingRuleRepository = processingRuleRepository;
        this.objectMapper = objectMapper;
        this.ruleCanvasValidator = ruleCanvasValidator;
    }

    public List<ProcessingRuleDto> listRules(String ruleType, Boolean enabled, String approvalStatus) {
        return processingRuleRepository.findAll().stream()
                .filter(rule -> ruleType == null || ruleType.equals(rule.getRuleType()))
                .filter(rule -> enabled == null || enabled == rule.isEnabled())
                .filter(rule -> approvalStatus == null || approvalStatus.equals(rule.getApprovalStatus()))
                .map(this::toDto)
                .toList();
    }

    public ProcessingRuleDetailDto getRule(UUID id) {
        return toDetailDto(findOrThrow(id));
    }

    @Transactional
    public ProcessingRuleDetailDto createRule(@Valid ProcessingRuleCreate request) {
        if (request.canvas() != null) {
            ruleCanvasValidator.validate(request.canvas());
        }
        ProcessingRuleEntity entity = new ProcessingRuleEntity();
        entity.setName(request.name());
        entity.setRuleType(request.ruleType());
        entity.setTriggerType(request.triggerType());
        entity.setDescription(request.description());
        entity.setEnabled(false);
        entity.setApprovalStatus("draft");
        if (request.canvas() != null) {
            entity.setCanvas(toJson(request.canvas()));
        }
        return toDetailDto(processingRuleRepository.save(entity));
    }

    @Transactional
    public ProcessingRuleDetailDto patchRule(UUID id, ProcessingRulePatch patch) {
        ProcessingRuleEntity entity = findOrThrow(id);
        if (patch.name() != null) {
            entity.setName(patch.name());
        }
        if (patch.description() != null) {
            entity.setDescription(patch.description());
        }
        if (patch.triggerType() != null) {
            entity.setTriggerType(patch.triggerType());
        }
        if (patch.canvas() != null) {
            ruleCanvasValidator.validate(patch.canvas());
            entity.setCanvas(toJson(patch.canvas()));
        }
        if (patch.enabled() != null) {
            if (Boolean.TRUE.equals(patch.enabled())) {
                RuleCanvasDto canvas = patch.canvas() != null
                        ? patch.canvas()
                        : parseCanvas(entity.getCanvas());
                if (canvas.nodes() != null && !canvas.nodes().isEmpty()) {
                    ruleCanvasValidator.validate(canvas);
                }
            }
            entity.setEnabled(patch.enabled());
        }
        return toDetailDto(processingRuleRepository.save(entity));
    }

    private ProcessingRuleEntity findOrThrow(UUID id) {
        return processingRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rule not found"));
    }

    private ProcessingRuleDto toDto(ProcessingRuleEntity entity) {
        return new ProcessingRuleDto(
                entity.getId(),
                entity.getName(),
                entity.getRuleType(),
                entity.isEnabled(),
                entity.getTriggerType(),
                entity.getLastRunAt(),
                entity.getApprovalStatus(),
                entity.getDescription()
        );
    }

    private ProcessingRuleDetailDto toDetailDto(ProcessingRuleEntity entity) {
        return new ProcessingRuleDetailDto(
                entity.getId(),
                entity.getName(),
                entity.getRuleType(),
                entity.isEnabled(),
                entity.getTriggerType(),
                entity.getLastRunAt(),
                entity.getApprovalStatus(),
                entity.getDescription(),
                parseCanvas(entity.getCanvas()),
                List.of()
        );
    }

    private RuleCanvasDto parseCanvas(String json) {
        try {
            return objectMapper.readValue(json, RuleCanvasDto.class);
        } catch (Exception e) {
            return new RuleCanvasDto(List.of(), List.of());
        }
    }

    private String toJson(RuleCanvasDto canvas) {
        try {
            return objectMapper.writeValueAsString(canvas);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid canvas");
        }
    }
}
