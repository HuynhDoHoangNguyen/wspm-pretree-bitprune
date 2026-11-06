package vn.edu.wspm.backend.controllers.api.v1.recommendation;

import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.recommendation.ComboReadResponse.Detail;
import vn.edu.wspm.backend.dto.recommendation.ComboReadResponse.TopK;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.recommendation.ComboReadService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
@RequestMapping(value = "/api/v1/recommendations/combos", produces = MediaType.APPLICATION_JSON_VALUE)
public class ComboReadController {
    private final ComboReadService service;

    public ComboReadController(ComboReadService service) { this.service = service; }

    @GetMapping
    public ApiResponse<TopK> topK(@RequestParam MultiValueMap<String, String> parameters,
            HttpServletRequest request) {
        validateParameters(parameters, Set.of("productId", "k"));
        String productId = identifier(parameters.getFirst("productId"), "productId");
        int k = ComboReadService.DEFAULT_K;
        if (parameters.containsKey("k")) {
            String value = parameters.getFirst("k");
            if (value == null || !value.matches("[0-9]+")) throw invalidK();
            try { k = Integer.parseInt(value); }
            catch (NumberFormatException invalid) { throw invalidK(); }
            if (k < 1 || k > ComboReadService.MAX_K) throw invalidK();
        }
        return ApiResponse.success(service.topK(productId, k), RequestIdFilter.requestId(request));
    }

    @GetMapping("/{comboId}")
    public ApiResponse<Detail> detail(@PathVariable("comboId") String comboId,
            @RequestParam MultiValueMap<String, String> parameters, HttpServletRequest request) {
        validateParameters(parameters, Set.of());
        return ApiResponse.success(service.detail(identifier(comboId, "comboId")),
                RequestIdFilter.requestId(request));
    }

    private static void validateParameters(MultiValueMap<String, String> values, Set<String> allowed) {
        for (var entry : values.entrySet()) {
            if (!allowed.contains(entry.getKey()))
                throw ApplicationException.invalid("query", "UNKNOWN_FIELD", "Query contains an unsupported parameter.");
            if (entry.getValue() == null || entry.getValue().size() != 1)
                throw ApplicationException.invalid(entry.getKey(), "DUPLICATE_VALUE", "Supply exactly one value.");
        }
    }
    private static String identifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 40
                || value.chars().anyMatch(Character::isISOControl))
            throw ApplicationException.invalid(field, "Supply a nonblank identifier of at most 40 characters.");
        return value;
    }
    private static ApplicationException invalidK() {
        return ApplicationException.invalid("k", "Use an integer from 1 to 20.");
    }
}
