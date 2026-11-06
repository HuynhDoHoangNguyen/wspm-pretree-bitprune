import React from 'react';
import { getMiningRun, listMiningRuns } from '../mining-run/MiningRun.api';
import type { MiningRunSummary } from '../mining-run/MiningRun.types';
import MiningComparison from './MiningComparison';
import type { MiningComparisonSource } from './MiningComparison.types';

/** Existing shared RUN API only. Details are requested for the selected pair, not every RUN. */
export const productionComparisonSource: MiningComparisonSource = {
  async load(signal) {
    const summaries: MiningRunSummary[] = [];
    const ids = new Set<string>();
    let page = 0;
    let totalPages = 1;
    while (page < totalPages) {
      if (signal.aborted) throw new Error('Comparison cancelled');
      const result = await listMiningRuns({ page, size: 100, algorithm: '', status: '' }, signal);
      if (result.page !== page || !Number.isSafeInteger(result.totalPages) || result.totalPages < 0) {
        throw new Error('Invalid RUN pagination');
      }
      for (const run of result.items) {
        if (ids.has(run.runId)) throw new Error('RUN list changed; retry required');
        ids.add(run.runId);
        summaries.push(run);
      }
      totalPages = result.totalPages;
      page++;
    }
    return { summaries, runs: [], previewChecks: [] };
  },
  detail: getMiningRun,
};

export default function MiningComparisonPage() {
  return <MiningComparison source={productionComparisonSource}/>;
}
