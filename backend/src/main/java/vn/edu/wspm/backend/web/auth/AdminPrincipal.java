package vn.edu.wspm.backend.web.auth;

import java.security.Principal;
import java.util.List;

/** Request-scoped identity built only after shared JWT/account/session verification. */
public record AdminPrincipal(String userId, List<String> roles) implements Principal {
    public AdminPrincipal { roles = List.copyOf(roles); }
    @Override public String getName() { return userId; }
    @Override public String toString() { return "AdminPrincipal[redacted]"; }
}
