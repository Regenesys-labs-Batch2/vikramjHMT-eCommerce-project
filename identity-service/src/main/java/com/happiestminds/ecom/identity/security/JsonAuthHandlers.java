package com.happiestminds.ecom.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happiestminds.ecom.identity.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAuthHandlers {

    private final ObjectMapper mapper;

    public JsonAuthHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> write(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    public AccessDeniedHandler accessDenied() {
        return (req, res, ex) -> write(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    public void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String code, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(),
                ApiError.of(status, code, message, req.getRequestURI(), null));
    }
}
