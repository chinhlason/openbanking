package vn.com.truongsonbank.auth.authentication.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
class AuthRequiredHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (protectedSessionEndpoint(request.getRequestURI())
                && (blank(request.getHeader("X-Session-Id")) || blank(request.getHeader("DPoP")))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean protectedSessionEndpoint(String path) {
        return path.contains("/v1/sessions/")
                || path.contains("/v1/devices/") && (path.endsWith("/trust")
                || path.contains("/biometric/")
                || path.contains("/passkey/"));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
