-- INT-09/10 REVIEW CANDIDATE. Outside application scan path. Requires reviewed V3.
-- No mining receipt is issued, and no policy is approved by this migration.
ALTER TABLE COMBO_BATCH ADD UNIQUE KEY uk_tv2_batch_run (batch_id, run_id);
ALTER TABLE MINING_RUN_COMPLETION ADD UNIQUE KEY uk_tv2_completion_run_digest (run_id, receipt_digest);

CREATE TABLE COMBO_BATCH_WRITE_EVIDENCE (
    request_identity VARBINARY(800) NOT NULL,
    batch_id VARCHAR(40) NOT NULL,
    run_id VARCHAR(40) NOT NULL,
    content_version SMALLINT UNSIGNED NOT NULL,
    content_digest BINARY(32) NOT NULL,
    content_bytes MEDIUMBLOB NOT NULL,
    evidence_digest BINARY(32) NOT NULL,
    evidence_bytes MEDIUMBLOB NOT NULL,
    mining_receipt_digest BINARY(32) NOT NULL,
    expected_representation MEDIUMBLOB NOT NULL,
    representation_digest BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (request_identity),
    UNIQUE KEY uk_tv2_write_batch (batch_id),
    CONSTRAINT fk_tv2_write_batch_run FOREIGN KEY (batch_id, run_id)
        REFERENCES COMBO_BATCH(batch_id, run_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tv2_write_mining_receipt FOREIGN KEY (run_id, mining_receipt_digest)
        REFERENCES MINING_RUN_COMPLETION(run_id, receipt_digest) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tv2_write_shape CHECK (OCTET_LENGTH(request_identity) BETWEEN 1 AND 800
        AND content_version=1 AND OCTET_LENGTH(content_bytes) BETWEEN 1 AND 1048576
        AND OCTET_LENGTH(evidence_bytes) BETWEEN 1 AND 1048576
        AND OCTET_LENGTH(expected_representation) BETWEEN 1 AND 2097152
        AND OCTET_LENGTH(content_bytes)+OCTET_LENGTH(evidence_bytes)+OCTET_LENGTH(expected_representation)<=4194304),
    CONSTRAINT chk_tv2_write_hashes CHECK (content_digest=UNHEX(SHA2(content_bytes,256))
        AND evidence_digest=UNHEX(SHA2(evidence_bytes,256))
        AND representation_digest=UNHEX(SHA2(expected_representation,256)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
