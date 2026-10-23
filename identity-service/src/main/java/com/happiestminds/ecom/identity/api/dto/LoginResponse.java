package com.happiestminds.ecom.identity.api.dto;

import java.util.List;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, String userId,
                            List<String> roles) {
}
