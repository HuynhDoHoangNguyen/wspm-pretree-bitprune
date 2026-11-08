-- Native WDM-06 publication; no legacy completion, data backfill or policy seed.
ALTER TABLE COMBO_BATCH ADD UNIQUE KEY uk_combo_batch_native_run(batch_id,run_id);
CREATE TABLE COMBO_BATCH_WRITE_EVIDENCE (
 request_identity VARBINARY(800) NOT NULL PRIMARY KEY,
 batch_id VARCHAR(40) NOT NULL,
 run_id VARCHAR(40) NOT NULL,
 content_version SMALLINT UNSIGNED NOT NULL,
 content_digest BINARY(32) NOT NULL,
 content_bytes MEDIUMBLOB NOT NULL,
 evidence_digest BINARY(32) NOT NULL,
 evidence_bytes MEDIUMBLOB NOT NULL,
 -- Compatibility field name: native RESULT_MANIFEST digest, never a fabricated legacy receipt.
 mining_receipt_digest BINARY(32) NOT NULL,
 resolved_context MEDIUMBLOB NULL,
 expected_representation MEDIUMBLOB NOT NULL,
 representation_digest BINARY(32) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_combo_native_batch(batch_id),
 CONSTRAINT fk_combo_native_batch FOREIGN KEY(batch_id,run_id) REFERENCES COMBO_BATCH(batch_id,run_id),
 CONSTRAINT fk_combo_native_publication FOREIGN KEY(run_id) REFERENCES RUN_RESULT_PUBLICATION(run_id),
 CONSTRAINT chk_combo_native_sizes CHECK(content_version=1 AND OCTET_LENGTH(request_identity) BETWEEN 1 AND 800
  AND OCTET_LENGTH(content_bytes) BETWEEN 1 AND 1048576 AND OCTET_LENGTH(evidence_bytes) BETWEEN 1 AND 1048576
  AND OCTET_LENGTH(expected_representation) BETWEEN 1 AND 2097152
  AND (resolved_context IS NULL OR OCTET_LENGTH(resolved_context) BETWEEN 1 AND 524288)
  AND OCTET_LENGTH(content_bytes)+OCTET_LENGTH(evidence_bytes)+OCTET_LENGTH(expected_representation)<=4194304),
 CONSTRAINT chk_combo_native_hashes CHECK(content_digest=UNHEX(SHA2(content_bytes,256))
  AND evidence_digest=UNHEX(SHA2(evidence_bytes,256))
  AND representation_digest=UNHEX(SHA2(expected_representation,256)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
