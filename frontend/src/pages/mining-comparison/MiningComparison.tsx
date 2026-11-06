import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Group, NativeSelect, Paper, ScrollArea, Stack, Table, Text, Title } from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { MiningRunDetail, MiningRunSummary } from '../mining-run/MiningRun.types';
import { MiningComparisonData, MiningComparisonSource } from './MiningComparison.types';
import { compareRuns, difference, displayNumber, nonNegative } from './MiningComparison.utils';
import ComparisonChart from './ComparisonChart';
import './MiningComparison.css';

export default function MiningComparison({ source, preview = false }: { source: MiningComparisonSource; preview?: boolean }) {
  useDocumentTitle('So sánh thuật toán Mining – Admin');
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'success'; data: MiningComparisonData | null }>({ status: 'loading', data: null });
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading', data: null });
    Promise.resolve().then(() => source.load(controller.signal)).then(data => {
      if (!controller.signal.aborted) setState({ status: 'success', data });
    }).catch(() => {
      if (!controller.signal.aborted) setState({ status: 'error', data: null });
    });
    return () => controller.abort();
  }, [source, attempt]);
  return <Stack spacing="xl">
    <div><Text color="blue" size="sm" weight={600}>ADMIN · PHÂN TÍCH THỰC NGHIỆM</Text>
      <Title order={1}>So sánh thuật toán Mining</Title>
      <Text color="dimmed" mt="xs">WSPM_PreTree và WSPM_BitPrune · Đối chiếu RUN đã ghi nhận</Text>
    </div>
    {state.status === 'loading' && <Text role="status">Đang tải RUN so sánh…</Text>}
    {state.status === 'error' && <Alert color="red" title="Không thể tải RUN">
      <Button onClick={() => setAttempt(value => value + 1)}>Thử lại</Button>
    </Alert>}
    {state.status === 'success' && state.data && <ComparisonData data={state.data} source={source} preview={preview}/>}
  </Stack>;
}

function RunSelection({ label, runs, id, onChange }: { label: string; runs: MiningRunSummary[]; id: string; onChange: (id: string) => void }) {
  const run = runs.find(item => item.runId === id);
  const detail = run as Partial<MiningRunDetail> | undefined;
  return <Paper withBorder radius="lg" p="lg">
    <NativeSelect label={label} value={id} data={runs.map(item => ({ value: item.runId, label: item.runId }))}
      onChange={event => onChange(event.currentTarget.value)}/>
    {run && <Stack spacing={5} mt="md">
      <Group position="apart"><Text size="sm" weight={600}>{run.algorithm}</Text><Badge color={run.status === 'SUCCEEDED' ? 'blue' : 'gray'}>{run.status}</Badge></Group>
      <Text size="sm">Dataset: {run.datasetSource} · {run.snapshotId || 'Chưa có snapshot'}</Text>
      <Text size="xs" color="dimmed">Thời điểm tạo (UTC): {run.createdAt}</Text>
      <Text size="sm">Minsup: {run.minsupMode} / {displayNumber(run.minsupConfigured)} · Hiệu lực: {displayNumber(run.effectiveThreshold)}</Text>
      {detail?.maxW !== undefined && <Text size="xs">Fraction: {displayNumber(detail.minsupRelativeFraction)} · MaxW: {displayNumber(detail.maxW)} · MinW: {displayNumber(detail.minW)} · MeanW: {displayNumber(detail.meanW)}</Text>}
    </Stack>}
  </Paper>;
}

function ComparisonData({ data, source, preview }: { data: MiningComparisonData; source: MiningComparisonSource; preview: boolean }) {
  const runs = data.summaries ?? data.runs;
  const aRuns = runs.filter(run => run.algorithm === 'WSPM_PRETREE');
  const bRuns = runs.filter(run => run.algorithm === 'WSPM_BITPRUNE');
  const [aId, setAId] = useState(aRuns[0]?.runId || '');
  const [bId, setBId] = useState(bRuns[0]?.runId || '');
  const a = aRuns.find(run => run.runId === aId);
  const b = bRuns.find(run => run.runId === bId);
  if (!runs.length) return <Alert color="blue">Chưa có RUN để so sánh.</Alert>;
  if (!a || !b) return <Alert color="blue">Không có RUN phù hợp: cần cả PreTree và BitPrune.</Alert>;
  return <>
    <div className="mining-comparison-pair">
      <RunSelection label="RUN A — WSPM_PreTree" runs={aRuns} id={aId} onChange={setAId}/>
      <RunSelection label="RUN B — WSPM_BitPrune" runs={bRuns} id={bId} onChange={setBId}/>
    </div>
    <PairDetails key={aId + ':' + bId} data={data} source={source} aId={aId} bId={bId} preview={preview}/>
  </>;
}

function PairDetails({ data, source, aId, bId, preview }: {
  data: MiningComparisonData; source: MiningComparisonSource; aId: string; bId: string; preview: boolean;
}) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'success'; runs?: MiningRunDetail[] }>({ status: 'loading' });
  useEffect(() => {
    if (!source.detail) return;
    const controller = new AbortController();
    setState({ status: 'loading' });
    Promise.all([source.detail(aId, controller.signal), source.detail(bId, controller.signal)]).then(([a, b]) => {
      if (a.runId !== aId || b.runId !== bId) throw new Error('Unexpected RUN identity');
      if (!controller.signal.aborted) setState({ status: 'success', runs: [a, b] });
    }).catch(() => {
      if (!controller.signal.aborted) setState({ status: 'error' });
    });
    return () => controller.abort();
  }, [source, aId, bId, attempt]);
  if (source.detail && state.status === 'loading') return <Text role="status">Đang tải chi tiết cặp RUN…</Text>;
  if (source.detail && state.status === 'error') return <Alert color="red" title="Không thể tải chi tiết RUN">
    <Button onClick={() => setAttempt(value => value + 1)}>Thử lại chi tiết</Button>
  </Alert>;
  const runs = source.detail ? state.runs : data.runs;
  const a = runs?.find(run => run.runId === aId);
  const b = runs?.find(run => run.runId === bId);
  if (!a || !b) return <Alert color="blue">Chưa có chi tiết cặp RUN.</Alert>;
  return <ComparisonValues a={a} b={b} data={data} preview={preview}/>;
}

function ComparisonValues({ a, b, data, preview }: { a: MiningRunDetail; b: MiningRunDetail; data: MiningComparisonData; preview: boolean }) {
  // Synthetic pair checks can unlock illustration only, never a production comparison.
  const check = preview ? data.previewChecks.find(item => item.preTreeRunId === a.runId && item.bitPruneRunId === b.runId) : undefined;
  const result = compareRuns(a, b, check);
  const raw = (value: number | null, unit = '') => nonNegative(value) ? displayNumber(value, unit) : 'Chưa có dữ liệu';
  const rows: [string, string, string, string][] = [
    ['Tổng thực thi (ms)', raw(a.totalExecutionMs), raw(b.totalExecutionMs), displayNumber(result.eligible ? difference(a.totalExecutionMs, b.totalExecutionMs) : null, 'ms')],
    ['Đỉnh JVM heap lấy mẫu (bytes)', raw(a.peakSampledJvmHeapBytes), raw(b.peakSampledJvmHeapBytes), displayNumber(result.eligible ? difference(a.peakSampledJvmHeapBytes, b.peakSampledJvmHeapBytes) : null, 'bytes')],
    ['Số pattern', raw(a.patternCount), raw(b.patternCount), displayNumber(preview || result.eligible ? difference(a.patternCount, b.patternCount) : null)],
    ['Speedup (PreTree / BitPrune)', '—', displayNumber(result.speedup, '×'), '—'],
    ['Runtime Reduction', '—', displayNumber(result.runtimeReduction, '%'), '—'],
    ['Memory Reduction (heap lấy mẫu)', '—', displayNumber(result.memoryReduction, '%'), '—'],
  ];
  if (!preview) {
    rows.splice(3, 0, ...([
      ['Số candidate', 'candidateCount'], ['Dataset I/O (ms)', 'datasetIoMs'],
      ['Tiền xử lý (ms)', 'preprocessingMs'], ['DFS mining (ms)', 'dfsMiningMs'],
      ['Toàn bộ thuật toán (ms)', 'completeAlgorithmMs'], ['Output I/O (ms)', 'outputIoMs'],
    ] as const).map(([label, key]): [string, string, string, string] => [label, raw(a[key]), raw(b[key]), '—']));
    rows.push(['Trạng thái', a.status, b.status, '—'],
      ['Bắt đầu (UTC)', a.startedAt || 'Chưa có dữ liệu', b.startedAt || 'Chưa có dữ liệu', '—'],
      ['Kết thúc (UTC)', a.finishedAt || 'Chưa có dữ liệu', b.finishedAt || 'Chưa có dữ liệu', '—']);
  }
  return <>
    <Alert color="blue" title="Giới hạn dữ liệu và phép đo">
      DTO chưa có K, toàn bộ cấu hình mining, điều kiện JVM/phần cứng hoặc đối chiếu đầy đủ pattern → exact support.
      {preview ? ' Cặp hợp lệ dưới đây dựa trên giả định bổ sung chỉ trong fixture, không phải bằng chứng thực nghiệm.'
        : ' Chỉ hiển thị số liệu nguồn; chưa thể xác nhận hai RUN có điều kiện đo tương đương.'}
      Runtime dùng totalExecutionMs (tổng thực thi), không trộn với DFS hoặc completeAlgorithmMs.
      Bộ nhớ là JVM heap lấy mẫu, không phải RAM phần cứng chính xác. Số pattern bằng nhau chưa chứng minh kết quả tương đương.
    </Alert>
    {!result.eligible && <Alert color="yellow" title={preview ? 'RUN không đủ điều kiện so sánh' : 'Chưa đủ dữ liệu để so sánh hợp lệ'} role="alert">
      <ul>{result.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
      Không tính chỉ số cải thiện hoặc hiển thị biểu đồ hiệu năng cho cặp này.
    </Alert>}
    {result.eligible && <>
      <Text color="blue" weight={600}>Cặp fixture đủ điều kiện minh họa — không kết luận nghiên cứu.</Text>
      {(result.speedup === null || result.memoryReduction === null) && <Alert color="yellow" title="Thiếu dữ liệu hiệu năng hoặc mẫu số bằng 0">
        Giá trị thiếu không được thay bằng 0. Speedup và Runtime Reduction cần cả hai thời gian dương; Memory Reduction cần heap PreTree dương.
      </Alert>}
      <div className="mining-comparison-kpis">
        {[
          ['Tổng thực thi', `${raw(a.totalExecutionMs, 'ms')} / ${raw(b.totalExecutionMs, 'ms')}`],
          ['Đỉnh JVM heap lấy mẫu', `${raw(a.peakSampledJvmHeapBytes, 'bytes')} / ${raw(b.peakSampledJvmHeapBytes, 'bytes')}`],
          ['Số pattern', `${raw(a.patternCount)} / ${raw(b.patternCount)}`],
          ['Speedup', displayNumber(result.speedup, '×')],
          ['Runtime Reduction', displayNumber(result.runtimeReduction, '%')],
          ['Memory Reduction', displayNumber(result.memoryReduction, '%')],
        ].map(([label, value]) => <Paper key={label} withBorder p="lg" radius="lg">
          <Text size="sm" color="dimmed">{label}</Text><Text color="blue" size="lg" weight={700}>{value}</Text>
        </Paper>)}
      </div>
      <Text size="xs" color="dimmed">Các cặp giá trị theo thứ tự PreTree / BitPrune. Reduction âm nghĩa là BitPrune dùng nhiều hơn trong fixture.</Text>
      <div className="mining-comparison-charts">
        <ComparisonChart title="Thời gian tổng thực thi" unit="ms" a={a.totalExecutionMs} b={b.totalExecutionMs}/>
        <ComparisonChart title="Đỉnh JVM heap lấy mẫu" unit="bytes" a={a.peakSampledJvmHeapBytes} b={b.peakSampledJvmHeapBytes}/>
        <ComparisonChart title="Số lượng pattern" unit="pattern" a={a.patternCount} b={b.patternCount}/>
      </div>
    </>}
    <Paper withBorder p="lg" radius="lg">
      <Title order={2} mb="sm">Bảng đối chiếu RUN</Title>
      <Text color="dimmed" size="xs" mb="sm">Số liệu nguồn; chênh lệch = BitPrune − PreTree. Không diễn giải cặp không hợp lệ thành kết luận hiệu năng.</Text>
      <ScrollArea><Table aria-label="Bảng so sánh tổng hợp" style={{ minWidth: 600 }}>
        <thead><tr><th>Chỉ số</th><th>PreTree</th><th>BitPrune</th><th>Chênh lệch</th></tr></thead>
        <tbody>{(preview || result.eligible ? rows : rows.filter(([label]) => !['Speedup (PreTree / BitPrune)', 'Runtime Reduction', 'Memory Reduction (heap lấy mẫu)'].includes(label))).map(([label, av, bv, delta]) => <tr key={label}><th scope="row">{label}</th><td>{av}</td><td>{bv}</td><td>{delta}</td></tr>)}</tbody>
      </Table></ScrollArea>
    </Paper>
  </>;
}
