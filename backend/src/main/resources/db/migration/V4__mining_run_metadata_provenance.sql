-- Additive WDM-06.2 metadata. Existing run data and V1/V2/V3 are untouched.
-- No legacy backfill: absent rows mean unverified provenance/publication.
CREATE TABLE `RUN_PROVENANCE` (
    `run_id` VARCHAR(40) NOT NULL,
    `provenance_version` VARCHAR(80) NOT NULL,
    `provenance_json` JSON NOT NULL,
    `captured_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`run_id`),
    CONSTRAINT `fk_run_provenance_run` FOREIGN KEY (`run_id`)
        REFERENCES `MINING_RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_run_provenance_document` CHECK (
        JSON_TYPE(`provenance_json`) = 'OBJECT'
        AND JSON_CONTAINS_PATH(`provenance_json`, 'one', '$.version') = 1
        AND JSON_TYPE(JSON_EXTRACT(`provenance_json`, '$.version')) = 'STRING'
        AND JSON_UNQUOTE(JSON_EXTRACT(`provenance_json`, '$.version')) = `provenance_version`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `RUN_RESULT_PUBLICATION` (
    `run_id` VARCHAR(40) NOT NULL,
    `publication_status` VARCHAR(24) NOT NULL DEFAULT 'NOT_PUBLISHED',
    `manifest_version` VARCHAR(80) NULL,
    `result_sha256` CHAR(64) NULL,
    `pattern_count` BIGINT UNSIGNED NULL,
    `pattern_item_count` BIGINT UNSIGNED NULL,
    `run_item_count` BIGINT UNSIGNED NULL,
    `pattern_weight_policy` VARCHAR(80) NULL,
    `publication_elapsed_ms` BIGINT UNSIGNED NULL,
    `committed_at` DATETIME(6) NULL,
    PRIMARY KEY (`run_id`),
    CONSTRAINT `fk_run_publication_run` FOREIGN KEY (`run_id`)
        REFERENCES `MINING_RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_run_publication_complete` CHECK (
        (`publication_status` = 'NOT_PUBLISHED'
            AND `manifest_version` IS NULL AND `result_sha256` IS NULL
            AND `pattern_count` IS NULL AND `pattern_item_count` IS NULL AND `run_item_count` IS NULL
            AND `pattern_weight_policy` IS NULL AND `publication_elapsed_ms` IS NULL AND `committed_at` IS NULL)
        OR (`publication_status` = 'COMMITTED'
            AND `manifest_version` IS NOT NULL AND CHAR_LENGTH(TRIM(`manifest_version`)) > 0
            AND `result_sha256` IS NOT NULL AND REGEXP_LIKE(`result_sha256`, '^[0-9a-f]{64}$', 'c')
            AND `pattern_count` IS NOT NULL AND `pattern_item_count` IS NOT NULL AND `run_item_count` IS NOT NULL
            AND `pattern_weight_policy` IS NOT NULL AND CHAR_LENGTH(TRIM(`pattern_weight_policy`)) > 0
            AND `publication_elapsed_ms` IS NOT NULL AND `committed_at` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
