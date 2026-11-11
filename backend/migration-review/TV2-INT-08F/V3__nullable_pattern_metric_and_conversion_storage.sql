-- TV2-INT-08F / V3 REVIEW CANDIDATE. MySQL 8.4 target.
-- Intentionally outside application Flyway scan path until deployment approval.
-- Tested through real Flyway V1/V2/V3 on owned disposable MySQL only.
-- Register NullablePatternMetricPreflight before eventual production promotion.
-- No data backfill, policy approval, publisher or completion trigger is installed.

ALTER TABLE PATTERN
    MODIFY COLUMN pattern_weight DOUBLE NULL,
    MODIFY COLUMN pattern_weight_policy VARCHAR(80) NULL,
    ADD CONSTRAINT chk_tv2_pattern_metric_pair CHECK (
        (pattern_weight IS NULL AND pattern_weight_policy IS NULL)
        OR (pattern_weight IS NOT NULL AND pattern_weight_policy IS NOT NULL
            AND CHAR_LENGTH(TRIM(pattern_weight_policy)) > 0)
    );

-- Required for a composite FK preventing a run input from selecting another snapshot's seal.
-- Existing run_id PK already implies uniqueness, so this does not rewrite row values.
ALTER TABLE MINING_RUN ADD UNIQUE KEY uk_tv2_run_snapshot (run_id, snapshot_id);

CREATE TABLE SNAPSHOT_CONVERSION_SEAL (
    conversion_id VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    snapshot_id VARCHAR(40) NOT NULL,
    conversion_policy VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    seal_version SMALLINT UNSIGNED NOT NULL,
    seal_digest BINARY(32) NOT NULL,
    raw_metadata_binding BINARY(32) NOT NULL,
    source_type VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    acquisition VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    source_namespace VARCHAR(160) NOT NULL,
    source_verification_scheme VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    raw_bytes MEDIUMBLOB NOT NULL,
    raw_sha256 BINARY(32) NOT NULL,
    raw_size INT UNSIGNED NOT NULL,
    sequence_bytes MEDIUMBLOB NOT NULL,
    sequence_sha256 BINARY(32) NOT NULL,
    sequence_size INT UNSIGNED NOT NULL,
    mapping_bytes MEDIUMBLOB NOT NULL,
    mapping_sha256 BINARY(32) NOT NULL,
    mapping_size INT UNSIGNED NOT NULL,
    statistics_bytes MEDIUMBLOB NOT NULL,
    statistics_sha256 BINARY(32) NOT NULL,
    sequence_count INT UNSIGNED NOT NULL,
    dictionary_count INT UNSIGNED NOT NULL,
    manifest_bytes MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (conversion_id),
    UNIQUE KEY uk_tv2_seal_snapshot_policy (snapshot_id, conversion_policy),
    UNIQUE KEY uk_tv2_seal_id_snapshot (conversion_id, snapshot_id),
    CONSTRAINT fk_tv2_seal_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES DATASET_SNAPSHOT(snapshot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tv2_seal_identity CHECK (CHAR_LENGTH(conversion_id) BETWEEN 1 AND 40
        AND conversion_policy = 'ORDERED_AT_PRODUCT_PRESENCE_V1' AND seal_version = 1),
    CONSTRAINT chk_tv2_seal_source CHECK (source_type = 'CSV' AND acquisition = 'CSV_UPLOAD'
        AND source_verification_scheme = 'CSV_UPLOAD_OPERATOR_ASSIGNED_V1'
        AND CHAR_LENGTH(TRIM(source_namespace)) > 0),
    CONSTRAINT chk_tv2_seal_counts CHECK (sequence_count BETWEEN 1 AND 2147483647
        AND dictionary_count BETWEEN 1 AND 2147483647),
    CONSTRAINT chk_tv2_seal_sizes CHECK (
        raw_size = OCTET_LENGTH(raw_bytes) AND raw_size BETWEEN 1 AND 8388608
        AND sequence_size = OCTET_LENGTH(sequence_bytes) AND sequence_size BETWEEN 1 AND 8388608
        AND mapping_size = OCTET_LENGTH(mapping_bytes) AND mapping_size BETWEEN 1 AND 8388608
        AND OCTET_LENGTH(statistics_bytes) BETWEEN 1 AND 1048576
        AND OCTET_LENGTH(manifest_bytes) BETWEEN 1 AND 262144
        AND OCTET_LENGTH(raw_bytes) + OCTET_LENGTH(sequence_bytes) + OCTET_LENGTH(mapping_bytes)
            + OCTET_LENGTH(statistics_bytes) + OCTET_LENGTH(manifest_bytes) <= 16777216
    ),
    CONSTRAINT chk_tv2_seal_hashes CHECK (
        raw_sha256 = UNHEX(SHA2(raw_bytes,256))
        AND sequence_sha256 = UNHEX(SHA2(sequence_bytes,256))
        AND mapping_sha256 = UNHEX(SHA2(mapping_bytes,256))
        AND statistics_sha256 = UNHEX(SHA2(statistics_bytes,256))
        AND seal_digest = UNHEX(SHA2(manifest_bytes,256))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE MINING_RUN_INPUT_BINDING (
    run_id VARCHAR(40) NOT NULL,
    snapshot_id VARCHAR(40) NOT NULL,
    conversion_id VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    request_identity VARBINARY(128) NOT NULL,
    input_version SMALLINT UNSIGNED NOT NULL,
    input_digest BINARY(32) NOT NULL,
    engine_revision VARCHAR(160) NOT NULL,
    engine_digest_scheme VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    engine_digest BINARY(32) NOT NULL,
    manifest_bytes MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_tv2_input_request (request_identity),
    UNIQUE KEY uk_tv2_input_run_digest (run_id, input_digest),
    CONSTRAINT fk_tv2_input_run_snapshot FOREIGN KEY (run_id, snapshot_id)
        REFERENCES MINING_RUN(run_id, snapshot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tv2_input_seal_snapshot FOREIGN KEY (conversion_id, snapshot_id)
        REFERENCES SNAPSHOT_CONVERSION_SEAL(conversion_id, snapshot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tv2_input_shape CHECK (OCTET_LENGTH(request_identity) BETWEEN 1 AND 128
        AND input_version = 1 AND CHAR_LENGTH(TRIM(engine_revision)) > 0
        AND engine_digest_scheme = 'JAR_ENTRIES_SHA256_V1'
        AND OCTET_LENGTH(manifest_bytes) BETWEEN 1 AND 262144),
    CONSTRAINT chk_tv2_input_hash CHECK (input_digest = UNHEX(SHA2(manifest_bytes,256)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Current ownership only. No append-only attempt-history claim is made by this proposal.
CREATE TABLE MINING_RUN_OWNERSHIP (
    run_id VARCHAR(40) NOT NULL,
    fence BIGINT UNSIGNED NOT NULL,
    attempt_token BINARY(16) NULL,
    worker_id VARCHAR(80) NULL,
    lease_until DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_tv2_owner_attempt (run_id, fence, attempt_token),
    CONSTRAINT fk_tv2_owner_input FOREIGN KEY (run_id)
        REFERENCES MINING_RUN_INPUT_BINDING(run_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tv2_owner_shape CHECK (
        (fence = 0 AND attempt_token IS NULL AND worker_id IS NULL AND lease_until IS NULL)
        OR (fence > 0 AND attempt_token IS NOT NULL AND worker_id IS NOT NULL
            AND CHAR_LENGTH(TRIM(worker_id)) > 0 AND lease_until IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE MINING_RUN_COMPLETION (
    run_id VARCHAR(40) NOT NULL,
    input_digest BINARY(32) NOT NULL,
    fence BIGINT UNSIGNED NOT NULL,
    attempt_token BINARY(16) NOT NULL,
    receipt_version SMALLINT UNSIGNED NOT NULL,
    receipt_digest BINARY(32) NOT NULL,
    result_map_digest BINARY(32) NOT NULL,
    persisted_payload_digest BINARY(32) NOT NULL,
    dictionary_digest BINARY(32) NOT NULL,
    pattern_count BIGINT UNSIGNED NOT NULL,
    pattern_item_count BIGINT UNSIGNED NOT NULL,
    dictionary_count INT UNSIGNED NOT NULL,
    manifest_bytes MEDIUMBLOB NOT NULL,
    committed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id),
    CONSTRAINT fk_tv2_completion_input FOREIGN KEY (run_id, input_digest)
        REFERENCES MINING_RUN_INPUT_BINDING(run_id, input_digest) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tv2_completion_attempt FOREIGN KEY (run_id, fence, attempt_token)
        REFERENCES MINING_RUN_OWNERSHIP(run_id, fence, attempt_token) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tv2_completion_shape CHECK (receipt_version = 1 AND fence > 0
        AND dictionary_count BETWEEN 1 AND 2147483647
        AND ((pattern_count = 0 AND pattern_item_count = 0)
            OR (pattern_count > 0 AND pattern_item_count >= pattern_count))
        AND OCTET_LENGTH(manifest_bytes) BETWEEN 1 AND 262144),
    CONSTRAINT chk_tv2_completion_hash CHECK (receipt_digest = UNHEX(SHA2(manifest_bytes,256)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
