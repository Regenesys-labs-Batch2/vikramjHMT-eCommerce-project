package com.happiestminds.ecom.identity.api.dto;

import java.util.List;

public record UserResponse(String id, String email, List<String> roles, ProfileDto profile) {
}
