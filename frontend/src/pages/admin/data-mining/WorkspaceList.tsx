import React, { useEffect, useState } from 'react';
import { Anchor, Button, Group, Pagination, Paper, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { Link, useSearchParams } from 'react-router-dom';
import { workspaceApi } from 'api/admin/data-mining';
import { RunRow, SnapshotRow, WorkspacePage } from 'api/admin/data-mining/workspace';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import { useWorkspaceRead } from './useWorkspaceRead';
import { localTime, ReadError, shortId, StateBadge } from './WorkspacePanels';
import CopyValue from './CopyValue';
import useWorkspaceStyles from './workspace.styles';

export default function WorkspaceList({ runs = false }: { runs?: boolean }) {
  const [params, setParams] = useSearchParams(), { classes } = useWorkspaceStyles();
  const [search, setSearch] = useState(params.get('search') || '');
  const selectedSearch = params.get('search') || '';
  useEffect(() => setSearch(selectedSearch), [selectedSearch]);
  const returnTo = (runs ? '/admin/mining-runs?' : '/admin/datasets?') + params.toString();
  const page = /^[1-9][0-9]{0,5}$/.test(params.get('page') || '') ? Number(params.get('page')) : 1;
  const keys = runs ? ['search', 'status', 'algorithm', 'snapshotId'] : ['search', 'validationStatus', 'conversionStatus'];
  const query: Record<string, string> = { page: String(page), size: '20' };
  keys.forEach(key => { const value = params.get(key); if (value) query[key] = value; });
  const binding = JSON.stringify([runs, query]);
  const read = useWorkspaceRead<WorkspacePage<SnapshotRow | RunRow>>(binding,
    signal => runs ? workspaceApi.runs(query, signal) : workspaceApi.snapshots(query, signal));
  function update(key: string, value: string | null) {
    const next = new URLSearchParams(params); next.delete('page');
    if (value) next.set(key, value); else next.delete(key); setParams(next);
  }
  return <Paper withBorder radius="md" p="lg"><Stack>
    <form aria-label={runs ? 'Tìm run' : 'Tìm snapshot'} onSubmit={e => { e.preventDefault(); update('search', search.trim()); }}>
      <Group align="end"><TextInput label={runs ? 'Tìm Run ID' : 'Tìm tên file hoặc Snapshot ID'} value={search} onChange={e => setSearch(e.currentTarget.value)} style={{ flex: '1 1 220px' }}/>
        <Button type="submit" variant="light">Tìm kiếm</Button><Button variant="subtle" onClick={read.refresh}>Làm mới danh sách</Button></Group>
    </form>
    <Group align="end">
      {runs ? <><Select label="Trạng thái run" clearable value={params.get('status')} onChange={v => update('status', v)} data={[
        { value: 'QUEUED', label: 'Đang chờ' }, { value: 'RUNNING', label: 'Đang chạy' }, { value: 'SUCCEEDED', label: 'Hoàn tất' }, { value: 'FAILED', label: 'Thất bại' }, { value: 'LEGACY_UNVERIFIED', label: 'Dữ liệu cũ' }]}/>
      <Select label="Lọc thuật toán" clearable value={params.get('algorithm')} onChange={v => update('algorithm', v)} data={['WSPM_PRETREE', 'WSPM_BITPRUNE']}/>
      {params.get('snapshotId') && <Button variant="subtle" onClick={() => update('snapshotId', null)}>Bỏ lọc snapshot</Button>}</>
        : <><Select label="Kiểm tra dữ liệu" clearable value={params.get('validationStatus')} onChange={v => update('validationStatus', v)} data={[
          { value: 'PENDING', label: 'Chờ kiểm tra' }, { value: 'VALID', label: 'Đã kiểm tra' }, { value: 'INVALID', label: 'Cần xử lý' }, { value: 'LEGACY_UNVERIFIED', label: 'Dữ liệu cũ' }]}/>
        <Select label="Chuyển đổi" clearable value={params.get('conversionStatus')} onChange={v => update('conversionStatus', v)} data={[
          { value: 'NOT_STARTED', label: 'Chưa chuyển đổi' }, { value: 'RUNNING', label: 'Đang chuyển đổi' }, { value: 'SUCCEEDED', label: 'Đã chuyển đổi' }, { value: 'FAILED', label: 'Thất bại' }]}/></>}
    </Group>
    <Text size="xs" color="dimmed">Mới nhất trước · 20 dòng mỗi trang · Thời gian UTC+07</Text>
    {read.loading && <Text role="status">Đang tải danh sách…</Text>}{read.error && <ReadError retry={read.refresh} error={read.error}/>}
    {read.value && <>
      {!read.value.items.length ? <Text role="status">Chưa có {runs ? 'run' : 'snapshot'} phù hợp. Thử đổi bộ lọc hoặc tạo dữ liệu mới.</Text>
        : <div className={classes.table} role="region" aria-label={runs ? 'Lịch sử run' : 'Danh sách snapshot'} tabIndex={0}><Table striped highlightOnHover>
          <thead><tr><th>{runs ? 'Lần chạy / Dữ liệu' : 'Dữ liệu / Snapshot'}</th><th>{runs ? 'Cấu hình' : 'Quy mô'}</th><th>Trạng thái</th><th>Thời gian (UTC+07)</th><th>Thao tác</th></tr></thead>
          <tbody>{read.value.items.map(item => {
            if ('runId' in item) {
              const row = item as RunRow;
              return <tr key={row.runId}><td>{isRunId(row.runId) ? <Anchor component={Link} state={{ returnTo }} to={'/admin/mining-runs/' + row.runId}>{shortId(row.runId)}</Anchor> : <Text size="sm">{row.runId}</Text>}
                <Text size="sm" weight={600}>{row.sourceName || 'Dữ liệu chưa có tên'}</Text>{isRunId(row.snapshotId) ? <Anchor size="xs" component={Link} to={'/admin/datasets/' + row.snapshotId}>Snapshot {shortId(row.snapshotId)}</Anchor> : <Text size="xs">Snapshot cũ: {row.snapshotId}</Text>}</td>
              <td><Text size="sm">{row.algorithm}</Text><Text size="xs">{row.minsupMode} · {row.configuredValue}</Text><Text size="xs">Patterns: {row.patternCount ?? 'Chưa có'}</Text></td>
              <td><StateBadge value={row.status}/></td><td><Text size="sm">{localTime(row.createdAt)}</Text><Text size="xs" color="dimmed">Bắt đầu: {localTime(row.startedAt)}<br/>Kết thúc: {localTime(row.finishedAt)}</Text></td>
              <td>{isRunId(row.runId) ? <Button component={Link} state={{ returnTo }} to={'/admin/mining-runs/' + row.runId} size="xs" variant="light">Xem run</Button> : <Text size="xs">ID cũ chưa hỗ trợ mở chi tiết</Text>}<details><summary>ID đầy đủ</summary><CopyValue label="Run ID" value={row.runId}/></details></td></tr>;
            }
            const row = item as SnapshotRow;
            const supported = isRunId(row.snapshotId) && row.validationStatus !== 'LEGACY_UNVERIFIED';
            return <tr key={row.snapshotId}><td>{supported ? <Anchor component={Link} state={{ returnTo }} to={'/admin/datasets/' + row.snapshotId} weight={600}>{row.sourceName || 'Dữ liệu chưa có tên'}</Anchor> : <Text weight={600}>{row.sourceName || 'Dữ liệu cũ'}</Text>}
              <Text size="xs" color="dimmed">{row.sourceType} · {shortId(row.snapshotId)}</Text></td>
            <td><Text size="sm">{row.sequenceCount ?? 'Chưa có'} sequence</Text><Text size="xs">{row.distinctItemCount ?? 'Chưa có'} item</Text></td>
            <td><Stack spacing={4}><StateBadge value={row.validationStatus}/>{row.conversionStatus && <StateBadge value={row.conversionStatus}/>}</Stack></td>
            <td>{localTime(row.createdAt)}</td><td><Stack spacing="xs">{supported ? <Button component={Link} state={{ returnTo }} size="xs" variant="light" to={'/admin/datasets/' + row.snapshotId}>Xem chi tiết</Button> : <Text size="xs">Dữ liệu cũ chưa hỗ trợ mở chi tiết/chạy lại</Text>}
              {supported && row.conversionStatus === 'SUCCEEDED' && <Button component={Link} size="xs" to={'/admin/mining-runs?snapshotId=' + row.snapshotId}>Cấu hình khai phá</Button>}
              <details><summary>ID đầy đủ</summary><CopyValue label="Snapshot ID" value={row.snapshotId}/></details></Stack></td></tr>;
          })}</tbody></Table></div>}
      <Group position="apart"><Text size="sm">{read.value.total} kết quả · Trang {read.value.page}</Text>
        {read.value.total > 20 && <Pagination page={page} total={Math.max(1, Math.ceil(read.value.total / 20))} onChange={n => { const next = new URLSearchParams(params); next.set('page', String(n)); setParams(next); }}/>}</Group>
    </>}
  </Stack></Paper>;
}
