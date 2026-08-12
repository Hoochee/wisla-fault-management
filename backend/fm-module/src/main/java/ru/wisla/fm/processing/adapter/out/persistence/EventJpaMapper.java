package ru.wisla.fm.processing.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ru.wisla.fm.processing.domain.Event;

/**
 * Hand-written both-ways mapping between the domain {@link Event} and its {@code events} row
 * (design decision D2, no MapStruct). {@code tags} and {@code attributes} stay JSON strings on both
 * sides, so nothing is ever re-serialized.
 */
@Component
public class EventJpaMapper {

    public EventJpaEntity toJpaEntity(Event event) {
        EventJpaEntity entity = new EventJpaEntity();
        entity.setId(event.getId());
        entity.setStatus(event.getStatus());
        entity.setSeverity(event.getSeverity());
        entity.setTitle(event.getTitle());
        entity.setDescription(event.getDescription());
        entity.setSourceId(event.getSourceId());
        entity.setCiId(event.getCiId());
        entity.setNodeFqdn(event.getNodeFqdn());
        entity.setSystemName(event.getSystemName());
        entity.setSubsystemName(event.getSubsystemName());
        entity.setAssignedUserId(event.getAssignedUserId());
        entity.setRootEventId(event.getRootEventId());
        entity.setRepeatCount(event.getRepeatCount());
        entity.setTags(event.getTags());
        entity.setAttributes(event.getAttributes());
        entity.setRawEventId(event.getRawEventId());
        entity.setItsmIncidentNumber(event.getItsmIncidentNumber());
        entity.setSourceAt(event.getSourceAt());
        entity.setLastRepeatAt(event.getLastRepeatAt());
        entity.setTakenAt(event.getTakenAt());
        entity.setClosedAt(event.getClosedAt());
        entity.setCreatedAt(event.getCreatedAt());
        entity.setUpdatedAt(event.getUpdatedAt());
        return entity;
    }

    public Event toDomain(EventJpaEntity entity) {
        Event event = new Event();
        event.setId(entity.getId());
        event.setStatus(entity.getStatus());
        event.setSeverity(entity.getSeverity());
        event.setTitle(entity.getTitle());
        event.setDescription(entity.getDescription());
        event.setSourceId(entity.getSourceId());
        event.setCiId(entity.getCiId());
        event.setNodeFqdn(entity.getNodeFqdn());
        event.setSystemName(entity.getSystemName());
        event.setSubsystemName(entity.getSubsystemName());
        event.setAssignedUserId(entity.getAssignedUserId());
        event.setRootEventId(entity.getRootEventId());
        event.setRepeatCount(entity.getRepeatCount());
        event.setTags(entity.getTags());
        event.setAttributes(entity.getAttributes());
        event.setRawEventId(entity.getRawEventId());
        event.setItsmIncidentNumber(entity.getItsmIncidentNumber());
        event.setSourceAt(entity.getSourceAt());
        event.setLastRepeatAt(entity.getLastRepeatAt());
        event.setTakenAt(entity.getTakenAt());
        event.setClosedAt(entity.getClosedAt());
        event.setCreatedAt(entity.getCreatedAt());
        event.setUpdatedAt(entity.getUpdatedAt());
        return event;
    }
}
