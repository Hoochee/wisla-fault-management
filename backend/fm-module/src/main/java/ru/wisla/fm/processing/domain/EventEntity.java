package ru.wisla.fm.processing.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 16)
    private String status = "new";

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "ci_id")
    private UUID ciId;

    @Column(name = "node_fqdn", length = 512)
    private String nodeFqdn;

    @Column(name = "system_name")
    private String systemName;

    @Column(name = "subsystem_name")
    private String subsystemName;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "root_event_id")
    private UUID rootEventId;

    @Column(name = "repeat_count", nullable = false)
    private int repeatCount = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String tags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String attributes = "{}";

    @Column(name = "raw_event_id")
    private UUID rawEventId;

    @Column(name = "itsm_incident_number", length = 64)
    private String itsmIncidentNumber;

    @Column(name = "source_at", nullable = false)
    private Instant sourceAt;

    @Column(name = "last_repeat_at")
    private Instant lastRepeatAt;

    @Column(name = "taken_at")
    private Instant takenAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public void setCiId(UUID ciId) {
        this.ciId = ciId;
    }

    public void setNodeFqdn(String nodeFqdn) {
        this.nodeFqdn = nodeFqdn;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setSubsystemName(String subsystemName) {
        this.subsystemName = subsystemName;
    }

    public void setAssignedUserId(UUID assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public void setRootEventId(UUID rootEventId) {
        this.rootEventId = rootEventId;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public void setRawEventId(UUID rawEventId) {
        this.rawEventId = rawEventId;
    }

    public void setSourceAt(Instant sourceAt) {
        this.sourceAt = sourceAt;
    }

    public void setLastRepeatAt(Instant lastRepeatAt) {
        this.lastRepeatAt = lastRepeatAt;
    }

    public void setTakenAt(Instant takenAt) {
        this.takenAt = takenAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getCiId() {
        return ciId;
    }

    public String getNodeFqdn() {
        return nodeFqdn;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getSubsystemName() {
        return subsystemName;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public UUID getRootEventId() {
        return rootEventId;
    }

    public UUID getRawEventId() {
        return rawEventId;
    }

    public String getTags() {
        return tags;
    }

    public String getItsmIncidentNumber() {
        return itsmIncidentNumber;
    }

    public void setItsmIncidentNumber(String itsmIncidentNumber) {
        this.itsmIncidentNumber = itsmIncidentNumber;
    }

    public String getAttributes() {
        return attributes;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public Instant getSourceAt() {
        return sourceAt;
    }

    public Instant getLastRepeatAt() {
        return lastRepeatAt;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
