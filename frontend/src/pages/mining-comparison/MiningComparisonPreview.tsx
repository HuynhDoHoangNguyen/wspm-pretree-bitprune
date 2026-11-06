import React, { useMemo, useState } from 'react';
import { Alert, Container, MantineProvider, NativeSelect } from '@mantine/core';
import MiningComparison from './MiningComparison';
import { comparisonFixture } from './MiningComparison.fixtures';
import { MiningComparisonSource } from './MiningComparison.types';

type Scenario = 'data' | 'loading' | 'empty' | 'error' | 'no-pair';
function sourceFor(scenario: Scenario): MiningComparisonSource {
  let failed = false;
  return { async load(signal) {
    if (scenario === 'loading') return new Promise((_, reject) => {
      const abort = () => reject(new Error('Preview cancelled'));
      if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true });
    });
    if (scenario === 'error' && !failed) { failed = true; throw new Error('Preview error'); }
    if (scenario === 'empty') return { runs: [], previewChecks: [] };
    if (scenario === 'no-pair') return { runs: comparisonFixture.runs.filter(run => run.algorithm === 'WSPM_PRETREE'), previewChecks: [] };
    return comparisonFixture;
  } };
}

export default function MiningComparisonPreview({ embedded = false }: { embedded?: boolean } = {}) {
  const [scenario, setScenario] = useState<Scenario>('data');
  const source = useMemo(() => sourceFor(scenario), [scenario]);
  const content = <>
    <Alert color="blue" mb="md">Dữ liệu minh họa — không phải kết quả Mining thực tế.</Alert>
    <NativeSelect label={embedded ? 'Tình huống so sánh' : 'Tình huống preview'} mb="xl" value={scenario} data={[
      { value: 'data', label: 'Có dữ liệu' }, { value: 'loading', label: 'Đang tải' },
      { value: 'empty', label: 'Không có RUN' }, { value: 'error', label: 'Lỗi một lần / Thử lại' },
      { value: 'no-pair', label: 'Không có RUN phù hợp' },
    ]} onChange={event => setScenario(event.currentTarget.value as Scenario)}/>
    <MiningComparison key={scenario} source={source} preview/>
  </>;
  if (embedded) return content;
  return <MantineProvider theme={{ colorScheme: 'light', primaryColor: 'blue' }}>
    <div className="mining-comparison-page"><Container size="xl" py="xl">{content}</Container></div>
  </MantineProvider>;
}
