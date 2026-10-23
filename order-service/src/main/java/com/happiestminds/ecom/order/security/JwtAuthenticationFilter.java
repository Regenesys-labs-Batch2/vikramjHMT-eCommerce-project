package com.happiestminds.ecom.order.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Not a Spring bean: SecurityConfig creates it so Boot does not also register it as a servlet filter.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JsonAuthHandlers handlers;

    public JwtAuthenticationFilter(JwtService jwtService, JsonAuthHandlers handlers) {
        this.jwtService = jwtService;
        this.handlers = handlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7).trim());
            List<?> roles = claims.get("roles", List.class);
            List<GrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            handlers.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }
}
