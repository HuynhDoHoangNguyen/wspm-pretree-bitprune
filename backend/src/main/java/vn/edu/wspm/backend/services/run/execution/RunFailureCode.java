package vn.edu.wspm.backend.services.run.execution;

/** Fixed safe DB diagnostics. Never persist an exception message or payload. */
public enum RunFailureCode {
    INPUT_REVERIFICATION_FAILED, ENGINE_EXECUTION_FAILED, ADMISSION_FAILED,
    CLAIM_NOT_CONFIRMED, EXECUTION_LEASE_EXPIRED
}
