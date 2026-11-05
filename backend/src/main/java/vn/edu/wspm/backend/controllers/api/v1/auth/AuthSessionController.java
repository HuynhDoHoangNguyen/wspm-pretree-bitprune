package vn.edu.wspm.backend.controllers.api.v1.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.auth.LoginRequest;
import vn.edu.wspm.backend.dto.auth.TokenResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.auth.AuthSessionService;
import vn.edu.wspm.backend.web.auth.*;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.io.IOException;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthSessionController {
    private final AuthSessionService sessions;
    private final AuthCookies cookies;
    private final ObjectMapper mapper;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TokenResponse> login(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        var input = LoginRequest.from(AuthJson.read(request, mapper));
        var grant = sessions.login(input.username(), input.password());
        cookies.setRefresh(response, grant.refreshToken(), grant.sessionExpiresAt());
        cookies.clearCsrf(request, response);
        return ApiResponse.success(grant.response(), RequestIdFilter.requestId(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthJson.empty(request);
        try {
            var grant = sessions.refresh(cookies.refresh(request));
            cookies.setRefresh(response, grant.refreshToken(), grant.sessionExpiresAt());
            return ApiResponse.success(grant.response(), RequestIdFilter.requestId(request));
        } catch (ApplicationException rejected) {
            if (rejected.status().value() == 401) cookies.clearRefresh(response);
            throw rejected;
        }
    }

    @GetMapping("/me")
    public ApiResponse<IdentityResponse> me(
            @RequestAttribute(AuthAccessFilter.IDENTITY) IdentityResponse identity,
            HttpServletRequest request) {
        return ApiResponse.success(identity, RequestIdFilter.requestId(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthJson.empty(request);
        sessions.logout(cookies.refresh(request));
        cookies.clearRefresh(response);
        cookies.clearCsrf(request, response);
    }
}
