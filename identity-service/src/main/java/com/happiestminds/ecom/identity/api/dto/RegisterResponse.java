package com.happiestminds.ecom.identity.api.dto;

import java.util.List;

public record RegisterResponse(String id, String email, List<String> roles) {
}
