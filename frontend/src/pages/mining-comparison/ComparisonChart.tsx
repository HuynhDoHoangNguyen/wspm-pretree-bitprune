import React from 'react';
import { Paper, Text, Title } from '@mantine/core';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { displayNumber, nonNegative } from './MiningComparison.utils';

export default function ComparisonChart({ title, unit, a, b }: { title: string; unit: string; a: number | null; b: number | null }) {
  const available = nonNegative(a) && nonNegative(b);
  return <Paper withBorder radius="lg" p="lg">
    <Title order={3} mb="md">{title}</Title>
    {available ? <>
      <Text size="xs" color="dimmed">Đơn vị: {unit}</Text>
      <div role="img" aria-label={`${title}: PreTree ${displayNumber(a, unit)}, BitPrune ${displayNumber(b, unit)}`} className="mining-comparison-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={[{ name: 'RUN đã chọn', PreTree: a, BitPrune: b }]} margin={{ left: 18, right: 12, top: 16, bottom: 12 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false}/>
            <XAxis dataKey="name"/>
            <YAxis width={74} tickFormatter={value => new Intl.NumberFormat('vi-VN', { notation: 'compact' }).format(value)} label={{ value: unit, angle: -90, position: 'insideLeft' }}/>
            <Tooltip<number | string, string> formatter={value => displayNumber(typeof value === 'number' ? value : null, unit)}/>
            <Legend/>
            <Bar dataKey="PreTree" fill="#1971c2" isAnimationActive={false}/>
            <Bar dataKey="BitPrune" fill="#74c0fc" isAnimationActive={false}/>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </> : <Text color="dimmed">Chưa có dữ liệu hợp lệ cho biểu đồ này.</Text>}
  </Paper>;
}
