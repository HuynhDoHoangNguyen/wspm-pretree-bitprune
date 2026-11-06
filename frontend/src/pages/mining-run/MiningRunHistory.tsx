import React, { useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Group, Loader, Modal, NativeSelect, Pagination, Paper, ScrollArea, Stack, Table, Text, Title } from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { useQuery, useQueryClient } from 'react-query';
import { authSession, useSharedAuth } from 'auth/session';
import AuthGate from 'auth/AuthGate';
import { BRAND_NAME } from 'constants/Brand';
import { getMiningRun, listMiningRuns, MiningRunApiError } from './MiningRun.api';
import { MiningAlgorithm, MiningRunDetail, MiningRunPage, MiningRunParams, MiningStatus } from './MiningRun.types';

export interface MiningRunDataSource {
  list: (params: MiningRunParams, signal?: AbortSignal) => Promise<MiningRunPage>;
  detail: (runId: string, signal?: AbortSignal) => Promise<MiningRunDetail>;
}

const algorithms = ['WSPM_PRETREE', 'WSPM_BITPRUNE'];
const statuses = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'];
const initialParams: MiningRunParams = { page: 0, size: 20, algorithm: '', status: '' };
const unavailable = 'Chưa có dữ liệu';

function errorText(error: unknown, detail = false) {
  if (error instanceof MiningRunApiError) {
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (error.status === 403) return 'Bạn không có quyền xem lịch sử Mining RUN.';
    if (error.status === 404 && detail) return 'Không tìm thấy RUN. Dữ liệu có thể đã thay đổi.';
    if (error.status === 400) return 'Bộ lọc hoặc tham số phân trang không hợp lệ.';
  }
  return 'Không thể tải dữ liệu. Vui lòng thử lại.';
}

function QueryError({ error, retry, detail = false }: { error: unknown; retry: () => void; detail?: boolean }) {
  return (
    <Alert color="red" title="Tải dữ liệu thất bại" role="alert">
      <Stack spacing="sm">
        <Text>{errorText(error, detail)}</Text>
        <Button onClick={retry} variant="outline">Thử lại</Button>
      </Stack>
    </Alert>
  );
}

const detailFields: [keyof MiningRunDetail, string][] = [
  ['runId', 'RUN ID'], ['algorithm', 'Thuật toán'], ['status', 'Trạng thái'],
  ['datasetSource', 'Nguồn dataset'], ['snapshotId', 'Snapshot ID'],
  ['validationStatus', 'Trạng thái kiểm tra snapshot'], ['sequenceCount', 'Số sequence'],
  ['distinctItemCount', 'Số item phân biệt'], ['minsupMode', 'Chế độ minsup'],
  ['minsupConfigured', 'Minsup cấu hình'], ['effectiveThreshold', 'Ngưỡng hiệu lực'],
  ['minsupRelativeFraction', 'Minsup tương đối (fraction)'],
  ['maxW', 'MaxW'], ['minW', 'MinW'], ['meanW', 'MeanW'],
  ['patternCount', 'Số pattern'], ['candidateCount', 'Số candidate'],
  ['datasetIoMs', 'Dataset I/O (ms)'], ['preprocessingMs', 'Tiền xử lý (ms)'],
  ['dfsMiningMs', 'DFS mining (ms)'], ['completeAlgorithmMs', 'Toàn bộ thuật toán (ms)'],
  ['outputIoMs', 'Output I/O (ms)'], ['totalExecutionMs', 'Tổng thực thi (ms)'],
  ['peakSampledJvmHeapBytes', 'Đỉnh JVM heap lấy mẫu (bytes)'],
  ['createdAt', 'Thời điểm tạo (UTC)'], ['startedAt', 'Bắt đầu (UTC)'],
  ['finishedAt', 'Kết thúc (UTC)'], ['errorMessage', 'Thông báo lỗi công khai'],
];

function RunDetail({ runId, source, cacheKey }: { runId: string; source: MiningRunDataSource; cacheKey: unknown[] }) {
  const query = useQuery(
    [...cacheKey, 'detail', runId],
    ({ signal }) => source.detail(runId, signal),
    { retry: false }
  );
  if (query.isLoading) return <Text role="status">Đang tải chi tiết RUN…</Text>;
  if (query.isError) return <QueryError error={query.error} retry={() => { void query.refetch(); }} detail/>;
  if (!query.data) return null;
  const data = query.data;
  return (
    <Table aria-label="Chi tiết RUN">
      <tbody>
        {detailFields.map(([field, label]) => (
          <tr key={field}>
            <th scope="row">{label}</th>
            <td style={{ overflowWrap: 'anywhere' }}>{data[field] ?? unavailable}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

let nextHistoryScope = 0;
const productionSource: MiningRunDataSource = { list: listMiningRuns, detail: getMiningRun };

export default function MiningRunHistory() {
  const state = useSharedAuth();
  const client = useQueryClient();
  // A page-local cache namespace contains no token and cannot reuse another mount's data.
  const [scope] = useState(() => ++nextHistoryScope);
  useEffect(() => {
    const clear = () => {
      void client.cancelQueries(['mining-runs', scope]);
      client.removeQueries(['mining-runs', scope]);
    };
    const unsubscribe = authSession.onClear(clear);
    return () => { unsubscribe(); clear(); };
  }, [client, scope]);
  if (state.status !== 'authenticated') {
    if (state.status !== 'anonymous') return <AuthGate/>;
    return <Alert color="blue" role="alert">Vui lòng đăng nhập tài khoản quản trị để xem lịch sử Mining RUN.</Alert>;
  }
  if (state.forbidden || !state.user?.roles.includes('ADMIN')) {
    return <Alert color="red" role="alert">Bạn không có quyền xem lịch sử Mining RUN.</Alert>;
  }
  return <MiningRunHistoryView key={state.user.userId}
    cacheKey={['mining-runs', scope, state.user.userId]} source={productionSource}/>;
}

export function MiningRunHistoryView({ source, cacheKey }: { source: MiningRunDataSource; cacheKey: unknown[] }) {
  useDocumentTitle(`Lịch sử Mining RUN – ${BRAND_NAME}`);
  const [params, setParams] = useState(initialParams);
  const [selectedRun, setSelectedRun] = useState<string | null>(null);
  const [refreshedParams, setRefreshedParams] = useState<MiningRunParams | null>(null);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const query = useQuery(
    [...cacheKey, 'list', params],
    ({ signal }) => source.list(params, signal),
    { retry: false }
  );
  const data = query.data;

  async function refresh() {
    setRefreshedParams(null);
    const result = await query.refetch();
    if (mounted.current && result.isSuccess) setRefreshedParams(params);
  }

  return (
    <Stack>
      <Group position="apart">
        <Title order={2}>Lịch sử Mining RUN</Title>
        <Button onClick={() => { void refresh(); }} disabled={query.isFetching}>Làm mới</Button>
      </Group>
      {refreshedParams === params && !query.isFetching && !query.isError && (
        <Text role="status" color="blue">Đã làm mới danh sách</Text>
      )}
      <Text color="dimmed">RUN mới nhất trước; cùng thời điểm được sắp theo RUN ID giảm dần.</Text>
      <Paper p="md" withBorder>
        <Group align="end">
          <NativeSelect
            label="Thuật toán"
            value={params.algorithm}
            data={[{ value: '', label: 'Tất cả thuật toán' }, ...algorithms]}
            onChange={event => setParams({ ...params, page: 0, algorithm: event.currentTarget.value as MiningAlgorithm | '' })}
          />
          <NativeSelect
            label="Trạng thái"
            value={params.status}
            data={[{ value: '', label: 'Tất cả trạng thái' }, ...statuses]}
            onChange={event => setParams({ ...params, page: 0, status: event.currentTarget.value as MiningStatus | '' })}
          />
          <NativeSelect
            label="Số RUN mỗi trang"
            value={String(params.size)}
            data={['20', '50', '100']}
            onChange={event => setParams({ ...params, page: 0, size: Number(event.currentTarget.value) })}
          />
          <Button variant="subtle" onClick={() => setParams(initialParams)}>Xóa bộ lọc</Button>
        </Group>
      </Paper>

      {query.isFetching && <Group role="status"><Loader size="sm"/><Text>Đang tải lịch sử RUN…</Text></Group>}
      {query.isError && <QueryError error={query.error} retry={() => { void query.refetch(); }}/>}
      {!query.isError && !query.isLoading && data && (
        <Paper p="md" withBorder>
          {data.items.length === 0 ? (
            <Stack>
              <Text role="status">Không có RUN phù hợp.</Text>
              {params.page > 0 && <Button onClick={() => setParams({ ...params, page: 0 })}>Về trang đầu</Button>}
            </Stack>
          ) : (
            <ScrollArea>
              <Table striped highlightOnHover aria-label="Lịch sử RUN">
                <thead><tr>
                  <th>RUN ID</th><th>Thuật toán</th><th>Trạng thái</th><th>Nguồn dataset</th>
                  <th>Thời điểm tạo (UTC)</th><th>Số pattern</th><th>Tổng thực thi (ms)</th><th>Chi tiết</th>
                </tr></thead>
                <tbody>{data.items.map(run => (
                  <tr key={run.runId}>
                    <td>{run.runId}</td><td>{run.algorithm}</td><td><Badge>{run.status}</Badge></td>
                    <td>{run.datasetSource}</td><td>{run.createdAt ?? unavailable}</td>
                    <td>{run.patternCount ?? unavailable}</td><td>{run.totalExecutionMs ?? unavailable}</td>
                    <td><Button size="xs" variant="subtle" aria-label={`Xem RUN ${run.runId}`} onClick={() => setSelectedRun(run.runId)}>Xem</Button></td>
                  </tr>
                ))}</tbody>
              </Table>
            </ScrollArea>
          )}
          <Group position="apart" mt="md">
            <Text size="sm">Tổng: {data.totalElements} RUN</Text>
            {data.totalPages > 0 && <Pagination
              page={data.page + 1}
              total={data.totalPages}
              onChange={page => { if (!query.isFetching) setParams({ ...params, page: page - 1 }); }}
            />}
          </Group>
        </Paper>
      )}

      <Modal opened={selectedRun !== null} onClose={() => setSelectedRun(null)} title="Chi tiết Mining RUN" size="xl">
        {selectedRun !== null && <RunDetail key={selectedRun} runId={selectedRun} source={source} cacheKey={cacheKey}/>}
      </Modal>
    </Stack>
  );
}
