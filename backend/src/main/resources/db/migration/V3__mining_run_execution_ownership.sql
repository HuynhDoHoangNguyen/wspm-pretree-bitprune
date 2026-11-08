-- Additive ownership for managed WDM executions. Existing rows remain unowned.
-- V1/V2 definitions and existing status/timestamp/data values are not rewritten.
ALTER TABLE `MINING_RUN`
    ADD COLUMN `execution_owner_id` VARCHAR(40) NULL,
    ADD COLUMN `execution_attempt_id` VARCHAR(40) NULL,
    ADD COLUMN `execution_lease_until` DATETIME(6) NULL,
    ADD KEY `idx_run_execution_lease` (`status`, `execution_lease_until`),
    ADD CONSTRAINT `chk_run_execution_ownership` CHECK (
        (`execution_owner_id` IS NULL AND `execution_attempt_id` IS NULL AND `execution_lease_until` IS NULL)
        OR (`execution_owner_id` IS NOT NULL AND `execution_attempt_id` IS NOT NULL AND `execution_lease_until` IS NOT NULL)
    );
