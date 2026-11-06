import React, { useMemo, useState } from 'react';
import { Alert, Container, MantineProvider, NativeSelect, Text } from '@mantine/core';
import MiningMonitoring from './MiningMonitoring';
import { monitoringRuns, PREVIEW_OBSERVED_AT } from './MiningMonitoring.fixtures';
import type { MiningMonitoringSource } from './MiningMonitoring.types';

type Scenario = 'all' | 'queued' | 'running' | 'succeeded' | 'failed' | 'missing-timestamp' | 'missing-metrics' | 'future-start' | 'empty' | 'loading' | 'error' | 'detail-error';
function sourceFor(scenario: Scenario): MiningMonitoringSource {
  let listFailed = false;
  let detailFailed = false;
  const runs = scenario === 'empty' ? [] : monitoringRuns.filter(run =>
    ['all', 'loading', 'error', 'detail-error'].includes(scenario) || run.runId === `monitor-${scenario}`
  );
  return {
    async list(params, signal) {
      if (scenario === 'loading') return new Promise((_, reject) => {
        const abort = () => reject(new Error('Preview cancelled'));
        if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
      });
      if (scenario === 'error' && !listFailed) { listFailed = true; throw new Error('Simulated load error'); }
      return { items: runs, page: params.page, size: params.size, totalElements: runs.length, totalPages: runs.length ? 1 : 0 };
    },
    async detail(runId) {
      if (scenario === 'detail-error' && !detailFailed) { detailFailed = true; throw new Error('Simulated detail error'); }
      const run = runs.find(item => item.runId === runId);
      if (!run) throw new Error('Unknown preview RUN');
      return run;
    },
  };
}

export default function MiningMonitoringPreview({ embedded = false }: { embedded?: boolean } = {}) {
  const [scenario, setScenario] = useState<Scenario>('all');
  const source = useMemo(() => sourceFor(scenario), [scenario]);
  const content = <>
    <Alert color="blue" mb="md">Dữ liệu minh họa — không phải kết quả Mining thực tế.</Alert>
    <Text size="xs" color="dimmed" mb="md">Mốc quan sát mô phỏng: {new Date(PREVIEW_OBSERVED_AT).toISOString()}. Không tự cập nhật trạng thái hay gọi API.</Text>
    <NativeSelect label={embedded ? 'Tình huống giám sát' : 'Tình huống preview'} value={scenario} mb="xl" data={[
      { value: 'all', label: 'Tất cả RUN' }, { value: 'queued', label: 'Đang chờ' },
      { value: 'running', label: 'Đang chạy' }, { value: 'succeeded', label: 'Thành công' },
      { value: 'failed', label: 'Thất bại' }, { value: 'missing-timestamp', label: 'Thiếu timestamp' },
      { value: 'missing-metrics', label: 'Thiếu metric' }, { value: 'future-start', label: 'Thời điểm bắt đầu trong tương lai' },
      { value: 'empty', label: 'Chưa có RUN' }, { value: 'loading', label: 'Đang tải' },
      { value: 'error', label: 'Lỗi tải / Thử lại' }, { value: 'detail-error', label: 'Lỗi chi tiết / Thử lại' },
    ]} onChange={event => setScenario(event.currentTarget.value as Scenario)}/>
    <MiningMonitoring key={scenario} source={source} observedAt={PREVIEW_OBSERVED_AT}
      historyHref={embedded ? '/__preview/admin-login?section=mining-runs' : (process.env.REACT_APP_ENABLE_MINING_PREVIEW === 'true' ? '/__preview/mining-runs' : null)}/>
  </>;
  if (embedded) return content;
  return <MantineProvider theme={{ colorScheme: 'light', primaryColor: 'blue' }}>
    <div className="mining-monitoring-page"><Container size="xl" py="xl">{content}</Container></div>
  </MantineProvider>;
}
