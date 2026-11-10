import React, { useEffect, useState } from 'react';
import { Alert, Button, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { listPublicCombos } from '../api/combos';
import { PublicTopK } from '../models/combos';
import ComboCard from './ComboCard';

type State = { productId: string } & (
  | { status: 'loading' | 'error' }
  | { status: 'success'; data: PublicTopK }
);

export default function ProductComboSection({ productId }: { productId: string }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<State>({ productId, status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ productId, status: 'loading' });
    const load = async () => {
      try {
        const data = await listPublicCombos(productId, controller.signal);
        if (active) setState({ productId, status: 'success', data });
      } catch {
        if (active && !controller.signal.aborted) setState({ productId, status: 'error' });
      }
    };
    void load();
    return () => { active = false; controller.abort(); };
  }, [productId, attempt]);

  const loading = state.productId !== productId || state.status === 'loading';
  return <section aria-label="Combo gợi ý"><Stack spacing="md">
    <Title order={2}>Combo gợi ý cho sản phẩm</Title>
    {loading ? <Text role="status">Đang tải Combo…</Text> : state.status === 'error' ? (
      <Alert role="alert" color="red" title="Không tải được Combo">
        <Button mt="sm" variant="light" onClick={() => { setState({ productId, status: 'loading' }); setAttempt(n => n + 1); }}>Thử lại</Button>
      </Alert>
    ) : state.status === 'success' && (state.data.items.length === 0 ? (
      <Text role="status" color="dimmed">Hiện chưa có combo được công bố phù hợp với sản phẩm này.</Text>
    ) : (
      <SimpleGrid cols={4} spacing="md" breakpoints={[{ maxWidth: 'md', cols: 2 }, { maxWidth: 'xs', cols: 1 }]}>
        {state.data.items.map(({ rank, combo }) => <ComboCard key={combo.comboId} combo={combo} rank={rank} productId={productId}/>)}
      </SimpleGrid>
    ))}
  </Stack></section>;
}
