package vn.com.truongsonbank.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.com.truongsonbank.shared.response.TsbResponse;

final class ServiceAuthVerificationFilter extends OncePerRequestFilter {
    private final ServiceJwtVerifier verifier;
    private final ServiceAuthProperties properties;
    private final ObjectMapper objectMapper;

    ServiceAuthVerificationFilter(ServiceJwtVerifier verifier, ServiceAuthProperties properties, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.properties = properties;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            write(response, ServiceAuthErrors.INVALID);
            return;
        }
        try {
            ServiceSecurityContextHolder.set(verifier.verify(authorization.substring(7).trim()));
            chain.doFilter(request, response);
        } catch (vn.com.truongsonbank.shared.exception.TsbException exception) {
            write(response, exception.error());
        } finally {
            ServiceSecurityContextHolder.clear();
        }
    }

    private void write(HttpServletResponse response, vn.com.truongsonbank.shared.exception.ErrorDescriptor error) throws IOException {
        response.setStatus(error.httpStatus());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(new TsbResponse<>(null, false, error.code(), error.defaultMessage(), null, Instant.now(), null)));
    }
}
