-- REVIEW ONLY: never loaded by Flyway or application startup; NOT EXECUTED.
-- For an explicitly selected V2 database. Bind the two ? placeholders using a
-- PreparedStatement: (1) operator-generated UUID <=40 chars, (2) role name <=100.
-- Read ROLE first. Existing CUSTOMER means no insert is needed; a differently
-- cased code must be reviewed, never silently rewritten. Duplicate constraints
-- deliberately fail. This creates reference data only, no account or password.
INSERT INTO ROLE (role_id, code, name, created_at)
VALUES (?, 'CUSTOMER', ?, UTC_TIMESTAMP(6));
