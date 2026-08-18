package ru.wisla.fm.processing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An event as the processing context owns it: a plain mutable model with no JPA mapping.
 * {@code tags} and {@code attributes} stay JSON strings so nothing is re-serialized on the way
 * to or from the {@code events} jsonb columns (design decision D2).
 */
public class Event {

    /** Statuses that count as still open for the dedup, threshold and correlation windows. */
    public static final List<String> ACTIVE_STATUSES = List.of("new", "in_progress", "maintenance", "deferred");

    private UUID id;
    private String status = "new";
    private String severity;
    private String title;
    private String description;
    private UUID sourceId;
    private UUID ciId;
    private String nodeFqdn;
    private String systemName;
    private String subsystemName;
    private UUID assignedUserId;
    private UUID rootEventId;
    private int repeatCount = 1;
    private String tags = "[]";
    private String attributes = "{}";
    private UUID rawEventId;
    private String itsmIncidentNumber;
    private Instant sourceAt;
    private Instant lastRepeatAt;
    private Instant takenAt;
    private Instant closedAt;
    private Instant acknowledgedAt;
    private UUID acknowledgedByUserId;
    private Instant silencedUntil;
    private UUID silencedByUserId;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Builds the event a raw event turns into. The configuration item may be absent, in which case
     * {@code ciId}, {@code systemName} and {@code subsystemName} stay unset.
     */
    public static Event fromRawEvent(IncomingRawEvent raw, CiSnapshot ci) {
        Event event = new Event();
        event.status = "new";
        event.severity = raw.severity();
        event.title = raw.title();
        event.description = raw.description();
        event.sourceId = raw.sourceId();
        event.nodeFqdn = raw.nodeFqdn();
        event.rawEventId = raw.id();
        event.sourceAt = raw.sourceAt();
        event.attributes = raw.payload();
        if (ci != null) {
            event.ciId = ci.id();
            event.systemName = ci.systemName();
            event.subsystemName = ci.subsystemName();
        }
        return event;
    }

    /** The fatal roll-up event a threshold rule creates, inheriting the trigger's topology. */
    public static Event synthetic(Event trigger, String title, String description, Instant now) {
        Event synthetic = new Event();
        synthetic.status = "new";
        synthetic.severity = "fatal";
        synthetic.title = title;
        synthetic.description = description;
        synthetic.sourceId = trigger.sourceId;
        synthetic.ciId = trigger.ciId;
        synthetic.nodeFqdn = trigger.nodeFqdn;
        synthetic.systemName = trigger.systemName;
        synthetic.subsystemName = trigger.subsystemName;
        synthetic.sourceAt = now;
        synthetic.attributes = "{\"synthetic\":true,\"ruleType\":\"threshold\"}";
        return synthetic;
    }

    public void registerRepeat(Instant now) {
        repeatCount = repeatCount + 1;
        lastRepeatAt = now;
    }

    /** Raises the severity only when the candidate ranks more severe. */
    public void escalateSeverity(String candidateSeverity) {
        if (SeverityRank.isMoreSevere(candidateSeverity, severity)) {
            severity = candidateSeverity;
        }
    }

    public void assignRoot(UUID rootId) {
        rootEventId = rootId;
    }

    public void acknowledge(Instant now, UUID actorUserId) {
        rejectIfTerminal("Cannot acknowledge closed or archived event");
        acknowledgedAt = now;
        acknowledgedByUserId = actorUserId;
    }

    public void assignTo(UUID userId) {
        assignedUserId = userId;
    }

    public void silenceUntil(Instant until, UUID actorUserId) {
        rejectIfTerminal("Cannot silence closed or archived event");
        silencedUntil = until;
        silencedByUserId = actorUserId;
    }

    public void take(Instant now, UUID actorUserId) {
        rejectIfTerminal("Cannot take closed or archived event");
        status = "in_progress";
        assignedUserId = actorUserId;
        takenAt = now;
    }

    public void close(Instant now) {
        if ("closed".equals(status)) {
            throw new IllegalStateException("Event is already closed");
        }
        status = "closed";
        closedAt = now;
    }

    public boolean isSilenced(Instant now) {
        return silencedUntil != null && silencedUntil.isAfter(now);
    }

    private void rejectIfTerminal(String message) {
        if ("closed".equals(status) || "archived".equals(status)) {
            throw new IllegalStateException(message);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getCiId() {
        return ciId;
    }

    public void setCiId(UUID ciId) {
        this.ciId = ciId;
    }

    public String getNodeFqdn() {
        return nodeFqdn;
    }

    public void setNodeFqdn(String nodeFqdn) {
        this.nodeFqdn = nodeFqdn;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSubsystemName() {
        return subsystemName;
    }

    public void setSubsystemName(String subsystemName) {
        this.subsystemName = subsystemName;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(UUID assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public UUID getRootEventId() {
        return rootEventId;
    }

    public void setRootEventId(UUID rootEventId) {
        this.rootEventId = rootEventId;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public UUID getRawEventId() {
        return rawEventId;
    }

    public void setRawEventId(UUID rawEventId) {
        this.rawEventId = rawEventId;
    }

    public String getItsmIncidentNumber() {
        return itsmIncidentNumber;
    }

    public void setItsmIncidentNumber(String itsmIncidentNumber) {
        this.itsmIncidentNumber = itsmIncidentNumber;
    }

    public Instant getSourceAt() {
        return sourceAt;
    }

    public void setSourceAt(Instant sourceAt) {
        this.sourceAt = sourceAt;
    }

    public Instant getLastRepeatAt() {
        return lastRepeatAt;
    }

    public void setLastRepeatAt(Instant lastRepeatAt) {
        this.lastRepeatAt = lastRepeatAt;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public void setTakenAt(Instant takenAt) {
        this.takenAt = takenAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public UUID getAcknowledgedByUserId() {
        return acknowledgedByUserId;
    }

    public void setAcknowledgedByUserId(UUID acknowledgedByUserId) {
        this.acknowledgedByUserId = acknowledgedByUserId;
    }

    public Instant getSilencedUntil() {
        return silencedUntil;
    }

    public void setSilencedUntil(Instant silencedUntil) {
        this.silencedUntil = silencedUntil;
    }

    public UUID getSilencedByUserId() {
        return silencedByUserId;
    }

    public void setSilencedByUserId(UUID silencedByUserId) {
        this.silencedByUserId = silencedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
