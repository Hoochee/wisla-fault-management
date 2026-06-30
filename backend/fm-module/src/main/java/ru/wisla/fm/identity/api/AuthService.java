package ru.wisla.fm.identity.api;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.common.api.UnauthorizedException;
import ru.wisla.fm.common.security.JwtService;
import ru.wisla.fm.config.JwtProperties;
import ru.wisla.fm.identity.domain.UserEntity;
import ru.wisla.fm.identity.persistence.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByLogin(request.login())
                .filter(UserEntity::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid login or password"));

        user.setLastLoginAt(Instant.now());
        String token = jwtService.createToken(user.getId(), user.getLogin());
        return new LoginResponse(token, "Bearer", jwtProperties.expirationSeconds(), toDto(user));
    }

    public UserDto getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toDto)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    public UserDto toDto(UserEntity user) {
        List<UUID> roleIds = user.getRoles().stream().map(r -> r.getId()).toList();
        return new UserDto(
                user.getId(),
                user.getLogin(),
                user.getFullName(),
                user.getEmail(),
                roleIds,
                user.getTeam(),
                user.isActive()
        );
    }
}
