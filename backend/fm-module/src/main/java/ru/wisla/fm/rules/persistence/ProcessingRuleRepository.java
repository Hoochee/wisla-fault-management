package ru.wisla.fm.rules.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;

import java.util.List;
import java.util.UUID;

public interface ProcessingRuleRepository extends JpaRepository<ProcessingRuleEntity, UUID> {

    List<ProcessingRuleEntity> findByEnabledTrue();

    List<ProcessingRuleEntity> findByEnabledTrueOrderByCreatedAtAsc();
}
