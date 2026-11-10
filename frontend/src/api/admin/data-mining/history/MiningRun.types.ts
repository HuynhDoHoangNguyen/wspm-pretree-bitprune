export type MiningAlgorithm = 'WSPM_PRETREE' | 'WSPM_BITPRUNE';
export type MiningStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'LEGACY_UNVERIFIED';

export interface MiningRunSummary {
  runId: string;
  algorithm: MiningAlgorithm;
  status: MiningStatus;
  datasetSource: string;
  snapshotId: string | null;
  minsupMode: string;
  minsupConfigured: number;
  effectiveThreshold: number;
  patternCount: number | null;
  candidateCount: number | null;
  totalExecutionMs: number | null;
  peakSampledJvmHeapBytes: number | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface MiningRunDetail extends MiningRunSummary {
  minsupRelativeFraction: number | null;
  maxW: number | null;
  minW: number | null;
  meanW: number | null;
  datasetIoMs: number | null;
  preprocessingMs: number | null;
  dfsMiningMs: number | null;
  completeAlgorithmMs: number | null;
  outputIoMs: number | null;
  errorMessage: string | null;
  sequenceCount: number | null;
  distinctItemCount: number | null;
  validationStatus: string | null;
}

export interface MiningRunPage {
  items: MiningRunSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MiningRunParams {
  page: number;
  size: number;
  algorithm: MiningAlgorithm | '';
  status: MiningStatus | '';
}
