package vn.edu.wspm.backend.web.wdm;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Closed request boundary for future run creation. No resource access or execution. */
@Component
public final class RunRequestReader {
    private static final Set<String> FIELDS = Set.of("snapshotId", "algorithm", "minsupMode", "minsupValue");
    // Local strict defaults: unrelated application mapper customizations cannot relax this contract.
    private static final ObjectReader JSON = JsonMapper.builder().build().reader()
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Validator validator;

    public RunRequestReader(Validator validator) {
        this.validator = validator;
    }

    public RunCreateRequest read(InputStream stream) throws IOException {
        JsonNode body;
        try {
            body = JSON.readTree(stream);
        } catch (JacksonException invalid) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        }
        if (body == null || !body.isObject()) {
            throw ApplicationException.invalid("body", "Supply a JSON object.");
        }
        for (String field : body.propertyNames()) {
            if (!FIELDS.contains(field)) {
                throw ApplicationException.invalid("body", "Unknown fields are not accepted.");
            }
        }
        String snapshotId = text(body, "snapshotId");
        String algorithm = text(body, "algorithm");
        String mode = text(body, "minsupMode");
        JsonNode value = body.get("minsupValue");
        if (value == null || !value.isNumber()) {
            throw ApplicationException.invalid("minsupValue", "Supply a numeric value.");
        }
        RunCreateRequest input = new RunCreateRequest(snapshotId, algorithm, mode, value.doubleValue());
        List<FieldErrorDetail> details = validator.validate(input).stream()
                .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(),
                        "INVALID_VALUE", "Supply a valid value."))
                .distinct()
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        if (!details.isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    ErrorCode.VALIDATION_FAILED.message(), details);
        }
        return input;
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isString()) {
            throw ApplicationException.invalid(field, "Supply a text value.");
        }
        return value.asString();
    }
}
