import React from 'react';
import { Alert, Paper, Stack, Text, Title } from '@mantine/core';
import { MiningRunStatus } from 'api/admin/data-mining';
import { formatStoredMetric, hasCurrentMetricScope, metricAvailability, miningMetricRows, MiningMetricRow } from './mining-run-metrics';

function MetricGroup({ title, rows }: { title: string; rows: MiningMetricRow[] }) {
  return <Stack spacing="xs"><Title order={4}>{title}</Title>
    <dl style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 260px), 1fr))', gap: 16, margin: 0 }}>
      {rows.map(row => <div key={row.key} role="group" aria-label={row.label} data-metric={row.key} style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
        <dt><Text component="span" size="sm" weight={600}>{row.label}{row.unit ? ' (' + row.unit + ')' : ''}</Text></dt>
        <dd style={{ margin: '4px 0 0' }}><Text size="xl" weight={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
          {row.value === null ? row.missingText ?? formatStoredMetric(null) : <data value={row.value}>{formatStoredMetric(row.value)}</data>}
        </Text><Text size="sm" color="dimmed">{row.scope}</Text></dd>
      </div>)}
    </dl>
  </Stack>;
}
export default function MiningRunMetricsPanel({ report }: { report: MiningRunStatus }) {
  const groups = miningMetricRows(report), known = hasCurrentMetricScope(report);
  return <Paper component="section" aria-label="Số đo lần chạy" withBorder radius="md" p="lg"><Stack spacing="lg">
    <Title order={3}>Số đo lần chạy</Title>
    <Text>{metricAvailability(report)}</Text>
    {!known && <Alert color="yellow" title="Phạm vi đo chưa xác định">Các giá trị đã lưu được giữ nguyên; chưa xác minh chúng theo contract WDM-05.6.</Alert>}
    <MetricGroup title="Bộ đếm (số lượng)" rows={groups.counts}/>
    <MetricGroup title="Thời gian (ms)" rows={groups.timings}/>
    {known && <Text size="sm">Các scope thời gian có phần lồng nhau, không cộng các hàng để suy ra tổng. Giá trị ms được chia nguyên từ nanos; 0 ms có thể là phép đo dưới 1 ms.</Text>}
    <MetricGroup title="Heap lấy mẫu (bytes)" rows={groups.heap}/>
  </Stack></Paper>;
}
