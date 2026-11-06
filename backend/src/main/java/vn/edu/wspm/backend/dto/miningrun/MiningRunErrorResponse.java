package vn.edu.wspm.backend.dto.miningrun;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MiningRunErrorResponse(int status, String code, String message, String field) {
}
