package vn.edu.wspm.backend.web.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.server.RequestPath;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class ApiPaths {
    private static final PathPattern API = pattern("/api/**");
    private static final PathPattern AUTH = pattern("/api/v1/auth/**");
    private static final PathPattern ADMIN = pattern("/api/v1/admin/**");

    private ApiPaths() {}

    public static boolean isApi(HttpServletRequest request) {
        try {
            return matches(API, request);
        } catch (IllegalArgumentException malformed) {
            return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
        }
    }

    public static boolean isAuth(HttpServletRequest request) {
        return matches(AUTH, request);
    }

    public static boolean isWdm(HttpServletRequest request) {
        return matches(ADMIN, request);
    }

    private static boolean matches(PathPattern pattern, HttpServletRequest request) {
        return pattern.matches(
                RequestPath.parse(request.getRequestURI(), request.getContextPath())
                        .pathWithinApplication());
    }

    private static PathPattern pattern(String path) {
        return PathPatternParser.defaultInstance.parse(path);
    }
}
