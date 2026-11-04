package vn.edu.wspm.backend.services.auth;

import jakarta.validation.Validator;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.auth.RegistrationRequest;
import vn.edu.wspm.backend.entities.CustomerEntity;
import vn.edu.wspm.backend.entities.UserAccountEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.IdentityMapper;
import vn.edu.wspm.backend.repositories.auth.RegistrationRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RegistrationService {
    private static final Pattern IDENTITY_KEY =
            Pattern.compile(
                    "for key '(?:[^']*\\.)?(?:uk_v2_user_username|uk_v2_user_email)'",
                    Pattern.CASE_INSENSITIVE);
    private final RegistrationRepository repository;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final IdentityMapper identityMapper;
    private final TransactionTemplate transaction;
    private final Validator validator;

    public RegistrationService(
            RegistrationRepository repository,
            PasswordEncoder passwords,
            Clock clock,
            PlatformTransactionManager transactions,
            Validator validator,
            IdentityMapper identityMapper) {
        this.repository = repository;
        this.passwords = passwords;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.validator = validator;
        this.identityMapper = identityMapper;
    }

    public IdentityResponse register(RegistrationRequest input) {
        RegistrationRequest request = input.validated();
        if (!validator.validate(request).isEmpty()) {
            throw ApplicationException.invalid(
                    "email", "Supply a valid ASCII email address of at most 254 characters.");
        }
        // Deliberately outside the DB transaction: bcrypt does not hold database locks.
        String hash = passwords.encode(request.password());
        try {
            IdentityResponse result =
                    transaction.execute(
                            status -> {
                                var role =
                                        repository
                                                .customerRole()
                                                .orElseThrow(
                                                        () -> {
                                                            log.warn(
                                                                    "Customer registration"
                                                                        + " unavailable: CUSTOMER"
                                                                        + " role is not"
                                                                        + " provisioned");
                                                            return new ApplicationException(
                                                                    ErrorCode.SERVICE_UNAVAILABLE,
                                                                    "Customer registration is"
                                                                        + " unavailable.");
                                                        });
                                if (repository.identityExists(request.username(), request.email()))
                                    throw conflict();
                                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                                var account = new UserAccountEntity();
                                account.setUserId(UUID.randomUUID().toString());
                                account.setUsername(request.username());
                                account.setEmail(request.email());
                                account.setPasswordHash(hash);
                                account.setDisplayName(request.displayName());
                                account.setStatus("ACTIVE");
                                account.setCreatedAt(now);
                                account.setUpdatedAt(now);
                                var customer =
                                        new CustomerEntity(
                                                UUID.randomUUID().toString(),
                                                account.getUserId(),
                                                "anon-" + UUID.randomUUID(),
                                                now);
                                repository.persist(account, customer, role);
                                return identityMapper.toResponse(account, List.of("CUSTOMER"));
                            });
            log.info("Customer registration committed: userId={}", result.userId());
            return result;
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql
                        && sql.getErrorCode() == 1062
                        && sql.getMessage() != null
                        && IDENTITY_KEY.matcher(sql.getMessage()).find()) {
                    throw conflict();
                }
            }
            if (failure instanceof ApplicationException known) throw known;
            // Retain the service stack without exception messages/causes containing SQL/PII.
            var safe =
                    new IllegalStateException(
                            "Registration persistence failed: "
                                    + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Customer registration failed", safe);
            boolean unavailable =
                    failure instanceof DataAccessResourceFailureException
                            || failure instanceof CannotCreateTransactionException;
            throw new ApplicationException(
                    unavailable ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.INTERNAL_ERROR,
                    "Customer registration could not be completed.");
        }
    }

    private static ApplicationException conflict() {
        log.warn("Customer registration rejected: identity conflict");
        return new ApplicationException(
                ErrorCode.IDENTITY_CONFLICT, "Account identity is already in use.");
    }
}
