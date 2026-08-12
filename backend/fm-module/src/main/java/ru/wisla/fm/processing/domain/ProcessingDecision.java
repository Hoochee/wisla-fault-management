package ru.wisla.fm.processing.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProcessingDecision(
        boolean dedupEnabled,
        DedupPolicy dedupPolicy,
        UUID dedupRuleId,
        List<ThresholdIntent> thresholdIntents,
        List<CorrelationIntent> correlationIntents,
        List<NotifyIntent> notifyIntents,
        List<PushIntent> pushIntents,
        Set<UUID> executedRuleIds
) {

    public static ProcessingDecision empty() {
        return new ProcessingDecision(
                false, DedupPolicy.defaults(), null, List.of(), List.of(), List.of(), List.of(), Set.of());
    }

    public record ThresholdIntent(UUID ruleId, int count, int windowMin) {
    }

    public record CorrelationIntent(UUID ruleId, CorrelationPolicy policy) {
    }

    public record NotifyIntent(UUID ruleId, String channel, String emailAddress) {
    }

    public record PushIntent(UUID ruleId, String message) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean dedupEnabled;
        private DedupPolicy dedupPolicy = DedupPolicy.defaults();
        private UUID dedupRuleId;
        private final List<ThresholdIntent> thresholdIntents = new ArrayList<>();
        private final List<CorrelationIntent> correlationIntents = new ArrayList<>();
        private final List<NotifyIntent> notifyIntents = new ArrayList<>();
        private final List<PushIntent> pushIntents = new ArrayList<>();
        private final Set<UUID> executedRuleIds = new LinkedHashSet<>();

        public Builder enableDedup(UUID ruleId, DedupPolicy policy) {
            if (!dedupEnabled) {
                dedupEnabled = true;
                dedupPolicy = policy;
                dedupRuleId = ruleId;
            }
            return this;
        }

        public Builder addThreshold(UUID ruleId, ThresholdPolicy policy) {
            thresholdIntents.add(new ThresholdIntent(ruleId, policy.count(), policy.windowMin()));
            return this;
        }

        public Builder addCorrelation(UUID ruleId, CorrelationPolicy policy) {
            correlationIntents.add(new CorrelationIntent(ruleId, policy));
            return this;
        }

        public Builder addNotify(UUID ruleId, String channel, String emailAddress) {
            notifyIntents.add(new NotifyIntent(ruleId, channel, emailAddress));
            return this;
        }

        public Builder addPush(UUID ruleId, String message) {
            pushIntents.add(new PushIntent(ruleId, message));
            return this;
        }

        public Builder markExecuted(UUID ruleId) {
            executedRuleIds.add(ruleId);
            return this;
        }

        public ProcessingDecision build() {
            return new ProcessingDecision(
                    dedupEnabled,
                    dedupPolicy,
                    dedupRuleId,
                    List.copyOf(thresholdIntents),
                    List.copyOf(correlationIntents),
                    List.copyOf(notifyIntents),
                    List.copyOf(pushIntents),
                    Set.copyOf(executedRuleIds)
            );
        }
    }
}
