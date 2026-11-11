package vn.edu.wspm.backend.controllers.api.v1.recommendation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.recommendation.ComboReadResponse.Personalized;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.recommendation.ComboReadService;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
public class PersonalComboController {
    private final SharedRequestAuthenticator auth;
    private final ComboReadService service;
    public PersonalComboController(SharedRequestAuthenticator auth, ComboReadService service) {
        this.auth = auth; this.service = service;
    }
    @GetMapping(value = "/api/v1/recommendations/combos/personalized", produces = "application/json")
    public ApiResponse<Personalized> read(@RequestParam MultiValueMap<String, String> parameters,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var identity = auth.authenticate(request);
        if (parameters.keySet().stream().anyMatch(key -> !"productId".equals(key) && !"comboId".equals(key)))
            throw ApplicationException.invalid("query", "UNKNOWN_FIELD", "Only productId or comboId is supported.");
        if (parameters.containsKey("productId") && parameters.get("productId").size() != 1)
            throw ApplicationException.invalid("productId", "DUPLICATE_VALUE", "Supply exactly one value.");
        if (parameters.containsKey("comboId")) {
            if (parameters.get("comboId").size() != 1 || parameters.containsKey("productId"))
                throw ApplicationException.invalid("comboId", "INVALID_CONTEXT", "Supply one comboId without productId.");
            return ApiResponse.success(service.personalizedCombo(identity.userId(), parameters.getFirst("comboId")),
                    RequestIdFilter.requestId(request));
        }
        String productId = parameters.getFirst("productId");
        return ApiResponse.success(productId == null ? service.personalized(identity.userId())
                : service.personalized(identity.userId(), productId), RequestIdFilter.requestId(request));
    }
}
