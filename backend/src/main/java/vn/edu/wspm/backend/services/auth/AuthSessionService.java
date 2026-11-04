package vn.edu.wspm.backend.services.auth;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.auth.TokenResponse;
import vn.edu.wspm.backend.entities.UserAccountEntity;
import vn.edu.wspm.backend.entities.UserSessionEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.IdentityMapper;
import vn.edu.wspm.backend.repositories.auth.AuthSessionRepository;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
public class AuthSessionService {
    private final AuthSessionRepository repository;
    private final PasswordEncoder passwords;
    private final JwtTokenService jwt;
    private final Clock clock;
    private final IdentityMapper identityMapper;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthSessionService(
            AuthSessionRepository repository,
            PasswordEncoder passwords,
            JwtTokenService jwt,
            Clock clock,
            PlatformTransactionManager transactions,
            IdentityMapper identityMapper) {
        this.repository = repository;
        this.passwords = passwords;
        this.jwt = jwt;
        this.clock = clock;
        transaction = new TransactionTemplate(transactions);
        this.identityMapper = identityMapper;
        dummyHash =
                passwords.encode(
                        UUID.randomUUID().toString()); // Timing only; never a usable account.
    }

    public Grant login(String username, String password) {
        return storage(
                "login",
                () -> {
                    String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
                    var snapshot =
                            transaction.execute(
                                    status ->
                                            repository.byUsername(normalizedUsername).orElse(null));
                    if (!matchesPassword(password, snapshot) || snapshot == null) {
                        throw JwtTokenService.unauthenticated();
                    }
                    // Password work stays outside the account lock. Recheck under lock before
                    // creating a session.
                    Grant result = transaction.execute(status -> createSession(snapshot));
                    log.info("Auth login committed: userId={}", result.response().user().userId());
                    return result;
                });
    }

    public Grant refresh(String raw) {
        Grant result = storage("refresh", () -> transaction.execute(status -> rotateRefresh(raw)));
        log.debug("Auth refresh committed: userId={}", result.response().user().userId());
        return result;
    }

    public void logout(String raw) {
        storage(
                "logout",
                () ->
                        transaction.execute(
                                status -> {
                                    revokeRefresh(raw);
                                    return null;
                                }));
        // An absent/stale cookie does not imply a row was revoked.
        log.info("Auth logout completed");
    }

    public IdentityResponse authenticate(String accessToken) {
        var claims = jwt.verify(accessToken);
        return storage(
                "identity",
                () ->
                        transaction.execute(
                                status ->
                                        currentIdentity(
                                                claims.getSubject(),
                                                claims.getClaimAsString("sid"),
                                                claims.getExpiresAt())));
    }

    private boolean matchesPassword(String password, UserAccountEntity snapshot) {
        try {
            return passwords.matches(
                    password, snapshot == null ? dummyHash : snapshot.getPasswordHash());
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }

    /** Called inside the transaction; the Grant is exposed only after commit. */
    private Grant createSession(UserAccountEntity snapshot) {
        var account = repository.account(snapshot.getUserId(), true);
        if (!eligible(account) || !snapshot.getPasswordHash().equals(account.getPasswordHash())) {
            throw JwtTokenService.unauthenticated();
        }
        Instant now = now();
        String refresh = newRefresh();
        var session = new UserSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUser(account);
        session.setRefreshTokenHash(hash(refresh));
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plusSeconds(7 * 24 * 60 * 60));
        repository.insert(session);
        account.setLastLoginAt(now);
        account.setUpdatedAt(now);
        var result = grant(account, session, refresh, now);
        repository.flush();
        return result;
    }

    /** Preserve account-before-session lock ordering and the original absolute expiry. */
    private Grant rotateRefresh(String raw) {
        String hash = refreshHash(raw);
        if (hash == null) throw JwtTokenService.unauthenticated();
        String owner =
                repository.ownerOfRefresh(hash).orElseThrow(JwtTokenService::unauthenticated);
        var account = repository.account(owner, true);
        var session = repository.lockRefresh(hash).orElseThrow(JwtTokenService::unauthenticated);
        Instant now = now();
        if (!eligible(account) || !active(session, owner, clock.instant())) {
            throw JwtTokenService.unauthenticated();
        }
        String refresh = newRefresh();
        session.setRefreshTokenHash(hash(refresh));
        session.setLastUsedAt(now);
        Grant result = grant(account, session, refresh, now);
        repository.flush();
        return result;
    }

    private void revokeRefresh(String raw) {
        String hash = refreshHash(raw);
        if (hash == null) return;
        var owner = repository.ownerOfRefresh(hash);
        if (owner.isEmpty()) return;
        repository.account(owner.get(), true);
        var session = repository.lockRefresh(hash);
        if (session.isPresent() && session.get().getRevokedAt() == null) {
            session.get().setRevokedAt(now());
            repository.flush();
        }
    }

    private IdentityResponse currentIdentity(
            String owner, String sessionId, Instant accessExpiresAt) {
        var account = repository.account(owner, false);
        var session = repository.session(sessionId);
        if (!eligible(account)
                || !active(session, owner, clock.instant())
                || !clock.instant().isBefore(accessExpiresAt)) {
            throw JwtTokenService.unauthenticated();
        }
        return identity(account);
    }

    private Grant grant(
            UserAccountEntity account, UserSessionEntity session, String refresh, Instant now) {
        var token =
                new TokenResponse(
                        jwt.issue(account.getUserId(), session.getSessionId(), now),
                        "Bearer",
                        900,
                        now.plusSeconds(900),
                        identity(account));
        return new Grant(token, refresh, session.getExpiresAt());
    }

    private IdentityResponse identity(UserAccountEntity account) {
        return identityMapper.toResponse(account, repository.roles(account.getUserId()));
    }

    private static boolean eligible(UserAccountEntity account) {
        return account != null && "ACTIVE".equals(account.getStatus());
    }

    private static boolean active(UserSessionEntity session, String owner, Instant now) {
        return session != null
                && owner.equals(session.getUser().getUserId())
                && session.getRevokedAt() == null
                && session.getExpiresAt().isAfter(now);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private String newRefresh() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String refreshHash(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return null;
        byte[] bytes = Base64.getUrlDecoder().decode(raw);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(raw)) return null;
        return hash(raw);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private <T> T storage(String operation, Supplier<T> work) {
        try {
            return work.get();
        } catch (ApplicationException expected) {
            throw expected;
        } catch (RuntimeException failure) {
            var safe =
                    new IllegalStateException(
                            "Auth persistence failure: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Auth operation failed: operation={}", operation, safe);
            boolean unavailable =
                    failure instanceof DataAccessResourceFailureException
                            || failure instanceof CannotCreateTransactionException;
            throw new ApplicationException(
                    unavailable ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.INTERNAL_ERROR,
                    "Authentication could not be completed.");
        }
    }

    public record Grant(TokenResponse response, String refreshToken, Instant sessionExpiresAt) {
        @Override
        public String toString() {
            return "Grant[redacted]";
        }
    }
}
