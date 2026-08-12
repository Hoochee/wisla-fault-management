package ru.wisla.fm.processing.adapter.out.cmdb;

import org.springframework.stereotype.Component;
import ru.wisla.fm.cmdb.service.CiService;
import ru.wisla.fm.processing.application.port.out.CiLookupPort;
import ru.wisla.fm.processing.domain.CiSnapshot;

import java.util.Optional;

@Component
public class CiLookupAdapter implements CiLookupPort {

    private final CiService ciService;

    public CiLookupAdapter(CiService ciService) {
        this.ciService = ciService;
    }

    @Override
    public Optional<CiSnapshot> findOrCreateByFqdn(String fqdn) {
        return ciService.findOrCreateByFqdn(fqdn)
                .map(ci -> new CiSnapshot(ci.getId(), ci.getFqdn(), ci.getSystemName(), ci.getSubsystemName()));
    }
}
