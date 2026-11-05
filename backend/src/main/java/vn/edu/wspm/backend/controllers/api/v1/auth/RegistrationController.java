package vn.edu.wspm.backend.controllers.api.v1.auth;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.auth.RegistrationRequest;
import vn.edu.wspm.backend.services.auth.RegistrationService;
import vn.edu.wspm.backend.web.auth.AuthJson;
import vn.edu.wspm.backend.web.auth.AuthRequestFilter;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.io.IOException;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class RegistrationController {
    private final RegistrationService service;
    private final ObjectMapper mapper;

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(
            @RequestAttribute(AuthRequestFilter.RAW_CSRF) CsrfToken token,
            HttpServletRequest request) {
        return ApiResponse.success(
                new CsrfResponse(token.getHeaderName(), token.getToken()),
                RequestIdFilter.requestId(request));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdentityResponse> register(HttpServletRequest request) throws IOException {
        return ApiResponse.success(
                service.register(RegistrationRequest.from(AuthJson.read(request, mapper))),
                RequestIdFilter.requestId(request));
    }

    public record CsrfResponse(String headerName, String token) {
        @Override
        public String toString() {
            return "CsrfResponse[redacted]";
        }
    }
}
