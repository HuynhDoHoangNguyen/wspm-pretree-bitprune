package vn.edu.wspm.backend.web.wdm;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared Auth integration seam, not a token verifier or a browser role check. A provider must
 * verify credentials, eligibility and its agreed ADMIN mapping. Return only for an eligible Admin;
 * reject with safe 401/403 ApplicationException. Zero/multiple provider beans leave the namespace
 * unavailable (503), even when one of several beans is marked primary.
 */
@FunctionalInterface
public interface WdmAdminAuthorizer {
    void requireAdmin(HttpServletRequest request);
}
