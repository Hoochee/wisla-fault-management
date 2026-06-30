package ru.wisla.fm.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.wisla.fm.common.api.ForbiddenException;
import ru.wisla.fm.common.api.UnauthorizedException;
import ru.wisla.fm.identity.domain.RoleEntity;
import ru.wisla.fm.identity.domain.UserEntity;
import ru.wisla.fm.identity.persistence.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuthorizationService(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public UUID requireUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return (UUID) auth.getPrincipal();
    }

    public void requireAdmin(UUID userId) {
        if (!hasAdminPermission(userId)) {
            throw new ForbiddenException("Admin permission required");
        }
    }

    public boolean hasAdminPermission(UUID userId) {
        return userRepository.findById(userId)
                .map(this::userHasAdmin)
                .orElse(false);
    }

    private boolean userHasAdmin(UserEntity user) {
        return user.getRoles().stream().anyMatch(this::roleHasAdmin);
    }

    private boolean roleHasAdmin(RoleEntity role) {
        return parsePermissions(role.getPermissions()).contains("admin");
    }

    private List<String> parsePermissions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
