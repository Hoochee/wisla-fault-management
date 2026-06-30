package ru.wisla.fm.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;

import java.io.IOException;
import java.util.List;
@Component
public class SourceApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String SOURCE_KEY_PARAM = "sourceKey";
    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String INGEST_PATH = "/api/v1/ingest";

    private final EventSourceRepository eventSourceRepository;
    private final PasswordEncoder passwordEncoder;

    public SourceApiKeyAuthenticationFilter(EventSourceRepository eventSourceRepository,
                                          PasswordEncoder passwordEncoder) {
        this.eventSourceRepository = eventSourceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INGEST_PATH.equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String sourceKey = resolveApiKey(request);
        if (sourceKey == null || sourceKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        eventSourceRepository.findByStatus("active").stream()
                .filter(source -> passwordEncoder.matches(sourceKey, source.getApiKeyHash()))
                .findFirst()
                .ifPresent(source -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            source.getId(),
                            sourceKey,
                            List.of(new SimpleGrantedAuthority("ROLE_SOURCE"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });

        filterChain.doFilter(request, response);
    }

    private String resolveApiKey(HttpServletRequest request) {
        String queryKey = request.getParameter(SOURCE_KEY_PARAM);
        if (queryKey != null && !queryKey.isBlank()) {
            return queryKey;
        }
        String headerKey = request.getHeader(API_KEY_HEADER);
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }
}
