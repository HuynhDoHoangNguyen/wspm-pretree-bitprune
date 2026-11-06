import React, { useState } from 'react';
import { Alert, Container, NativeSelect, Stack, Text } from '@mantine/core';
import { QueryClient, QueryClientProvider } from 'react-query';
import { MiningRunDataSource, MiningRunHistoryView } from './MiningRunHistory';
import { MiningRunDetail, MiningStatus } from './MiningRun.types';

type Scenario = 'data' | 'empty' | 'loading' | 'error' | 'detail-loading' | 'detail-error';
const statuses: MiningStatus[] = ['SUCCEEDED', 'FAILED', 'RUNNING', 'QUEUED', 'LEGACY_UNVERIFIED'];

// Synthetic UI examples only: these values make no claims about mining results.
const runs: MiningRunDetail[] = Array.from({ length: 45 }, (_, index) => {
  const status = statuses[index % statuses.length];
  const succeeded = status === 'SUCCEEDED';
  const finished = succeeded || status === 'FAILED';
  const createdAt = new Date(Date.UTC(2026, 10, 5, 12, 0, -index)).toISOString();
  return {
    runId: `preview-run-${String(45 - index).padStart(3, '0')}`,
    algorithm: index % 2 === 0 ? 'WSPM_PRETREE' : 'WSPM_BITPRUNE',
    status, datasetSource: 'UPLOAD', snapshotId: `preview-snapshot-${index + 1}`,
    minsupMode: 'ABSOLUTE', minsupConfigured: 2, effectiveThreshold: 2,
    patternCount: succeeded ? index * 3 : null, candidateCount: succeeded ? index * 7 : null,
    totalExecutionMs: finished ? 120 + index : null,
    peakSampledJvmHeapBytes: finished ? 16 * 1024 * 1024 : null,
    createdAt, startedAt: status === 'QUEUED' ? null : createdAt,
    finishedAt: finished ? createdAt : null,
    minsupRelativeFraction: null, maxW: null, minW: null, meanW: null,
    datasetIoMs: finished ? 10 : null, preprocessingMs: finished ? 20 : null,
    dfsMiningMs: finished ? 70 + index : null, completeAlgorithmMs: finished ? 90 + index : null,
    outputIoMs: finished ? 20 : null,
    errorMessage: status === 'FAILED' ? 'Lỗi giả lập để kiểm tra giao diện.' : null,
    sequenceCount: 100, distinctItemCount: 12, validationStatus: 'VALID',
  };
});

function loading<T>(signal?: AbortSignal): Promise<T> {
  // Keep loading visible until the user selects another scenario; no network or timers.
  return new Promise((_, reject) => {
    const abort = () => reject(new DOMException('Preview cancelled', 'AbortError'));
    if (signal?.aborted) abort();
    else signal?.addEventListener('abort', abort, { once: true });
  });
}

function createSource(scenario: Scenario): MiningRunDataSource {
  let listFailed = false;
  let detailFailed = false;
  return {
    async list(params, signal) {
      if (scenario === 'loading') return loading(signal);
      if (scenario === 'error' && !listFailed) {
        listFailed = true;
        throw new Error('Simulated preview error');
      }
      const filtered = scenario === 'empty' ? [] : runs.filter(run =>
        (!params.algorithm || run.algorithm === params.algorithm) && (!params.status || run.status === params.status)
      );
      return {
        items: filtered.slice(params.page * params.size, (params.page + 1) * params.size),
        page: params.page, size: params.size, totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / params.size),
      };
    },
    async detail(runId, signal) {
      if (scenario === 'detail-loading') return loading(signal);
      if (scenario === 'detail-error' && !detailFailed) {
        detailFailed = true;
        throw new Error('Simulated preview detail error');
      }
      const run = runs.find(item => item.runId === runId);
      if (!run) throw new Error('Unknown preview RUN');
      return run;
    },
  };
}

function PreviewSession({ scenario }: { scenario: Scenario }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: { queries: { retry: false, cacheTime: 0, refetchOnWindowFocus: false } },
  }));
  const [source] = useState(() => createSource(scenario));
  return (
    <QueryClientProvider client={client}>
      <MiningRunHistoryView source={source} cacheKey={['mining-run-preview', scenario]}/>
    </QueryClientProvider>
  );
}

export default function MiningRunHistoryPreview({ embedded = false }: { embedded?: boolean } = {}) {
  const [scenario, setScenario] = useState<Scenario>('data');
  const content = <Stack>
    <Alert color="blue" title="Dữ liệu giả — chỉ kiểm tra giao diện">
          Không dùng tài khoản Admin, API thật hoặc database. Đây không phải nghiệm thu authentication hay tích hợp API.
    </Alert>
    <NativeSelect label={embedded ? 'Tình huống lịch sử' : 'Tình huống preview'} value={scenario}
      onChange={event => setScenario(event.currentTarget.value as Scenario)} data={[
        { value: 'data', label: 'Danh sách (45 RUN giả)' },
        { value: 'empty', label: 'Không có dữ liệu' },
        { value: 'loading', label: 'Đang tải danh sách' },
        { value: 'error', label: 'Lỗi danh sách → Thử lại' },
        { value: 'detail-loading', label: 'Đang tải chi tiết' },
        { value: 'detail-error', label: 'Lỗi chi tiết → Thử lại' },
      ]}/>
    <Text size="sm" color="dimmed">Chọn tình huống khác để thoát trạng thái đang tải. Lỗi giả lập chỉ xảy ra lần đầu; bấm Thử lại để phục hồi.</Text>
    <PreviewSession key={scenario} scenario={scenario}/>
  </Stack>;
  return embedded ? content : <Container size="xl" py="xl">{content}</Container>;
}
