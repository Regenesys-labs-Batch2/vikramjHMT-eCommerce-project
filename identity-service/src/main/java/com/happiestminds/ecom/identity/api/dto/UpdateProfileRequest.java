package com.happiestminds.ecom.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^[0-9+\\- ]{0,20}$", message = "must contain only digits, spaces, + or -") String phone,
        @Size(max = 200) String addressLine1,
        @Size(max = 100) String city,
        @Size(max = 20) String postcode) {
}
