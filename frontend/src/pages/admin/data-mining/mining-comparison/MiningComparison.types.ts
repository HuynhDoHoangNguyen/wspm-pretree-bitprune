import { MiningRunDetail, MiningRunSummary } from '../../../../api/admin/data-mining/history/MiningRun.types';

/** Supplemental synthetic assumptions, deliberately NOT fields of the RUN API.
 * Real experiments require a reviewed source of configuration, measurement and
 * full pattern->support evidence before this seam can be connected to TV1.
 */
export interface PreviewPairCheck {
  origin: 'PREVIEW_ONLY';
  preTreeRunId: string;
  bitPruneRunId: string;
  configurationVerified: boolean;
  measurementConditionsVerified: boolean;
  fullPatternSupportMatch: boolean | null;
}

export interface MiningComparisonData {
  runs: MiningRunDetail[];
  previewChecks: PreviewPairCheck[];
  summaries?: MiningRunSummary[];
}

export interface MiningComparisonSource {
  load(signal: AbortSignal): Promise<MiningComparisonData>;
  detail?: (runId: string, signal: AbortSignal) => Promise<MiningRunDetail>;
}

export interface ComparisonResult {
  eligible: boolean;
  reasons: string[];
  speedup: number | null;
  runtimeReduction: number | null;
  memoryReduction: number | null;
}
