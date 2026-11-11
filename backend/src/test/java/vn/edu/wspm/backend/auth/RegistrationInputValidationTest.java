package vn.edu.wspm.backend.auth;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edu.wspm.backend.dto.auth.RegistrationRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.mappers.IdentityMapper;
import vn.edu.wspm.backend.repositories.auth.RegistrationRepository;
import vn.edu.wspm.backend.services.auth.RegistrationService;

import java.time.Clock;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** DTO construction must never bypass service validation, hashing order or persistence guards. */
class RegistrationInputValidationTest {
    private static ValidatorFactory validators;

    @BeforeAll static void validationProvider() { validators = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeProvider() { validators.close(); }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(new RegistrationRequest(null, "person@example.test", "Password123", "Name"), "username"),
                Arguments.of(new RegistrationRequest("customer.test", "person@example.test", "short", "Name"), "password"),
                Arguments.of(new RegistrationRequest("customer.test", "person@example.test", null, "Name"), "password"),
                Arguments.of(new RegistrationRequest(" CUSTOMER.TEST ", "not-an-email", "Password123", "Name"), "email"),
                Arguments.of(new RegistrationRequest("customer.test", "person@example.test", "Password123", "  "), "displayName"));
    }

    @ParameterizedTest(name = "{index}: {1}") @MethodSource("invalidInputs")
    void rejectsDirectServiceInputBeforeHashingOrStartingTransaction(RegistrationRequest input, String field) {
        var repository = mock(RegistrationRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var transactions = mock(PlatformTransactionManager.class);
        var clock = mock(Clock.class);
        var identities = mock(IdentityMapper.class);
        var service = new RegistrationService(repository, passwords, clock, transactions, validators.getValidator(), identities);

        var error = assertThrows(ApplicationException.class, () -> service.register(input));
        assertEquals(400, error.status().value());
        assertEquals("VALIDATION_FAILED", error.code());
        assertEquals(field, error.details().getFirst().field());
        verifyNoInteractions(repository, passwords, transactions, clock, identities);
    }
}
