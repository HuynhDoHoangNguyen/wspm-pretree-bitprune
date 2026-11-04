-- REVIEW ONLY. Not loaded by Flyway/startup; NOT EXECUTED on development DB.
-- Prerequisite: an independently reviewed exact ADMIN reference role exists.
-- Bind (1) existing assigning user_id, or NULL only for an approved operator
-- bootstrap; (2) target existing ACTIVE user_id. No account/password/role creation.
-- Use a caller-owned transaction. Require exactly one affected row before commit;
-- zero rows (missing/ineligible account or missing exact ADMIN role) must fail,
-- duplicate assignment/other constraint error must rollback. Record operator,
-- target DB/user and approval separately; do not infer identity from display name.
INSERT INTO USER_ROLE (user_id, role_id, assigned_at, assigned_by)
SELECT u.user_id, r.role_id, UTC_TIMESTAMP(6), ?
FROM USER_ACCOUNT u CROSS JOIN ROLE r
WHERE u.user_id = ?
  AND CAST(u.status AS BINARY) = CAST('ACTIVE' AS BINARY)
  AND CAST(r.code AS BINARY) = CAST('ADMIN' AS BINARY);
