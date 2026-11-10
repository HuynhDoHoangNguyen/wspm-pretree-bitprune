import React, { useEffect, useState } from 'react';
import { Alert, Button, Stack, Text, Title } from '@mantine/core';
import { listPublicCombos } from '../api/combos';
import { PublicTopK } from '../models/combos';
import ComboSelection from 'components/combo-recommendations/ComboSelection';
import usePersonalCombos from 'components/combo-recommendations/usePersonalCombos';

type State = { productId: string } & (
  | { status: 'loading' | 'error' }
  | { status: 'success'; data: PublicTopK }
);

export default function ProductComboSection({ productId }: { productId: string }) {
  const [attempt, setAttempt] = useState(0);
  const personal = usePersonalCombos(productId, attempt);
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
  const publicItems = !loading && state.status === 'success' ? state.data.items : [];
  const personalIds = new Set(personal.items.map(r => r.combo.comboId));
  const cards = [...personal.items.map(r => ({ combo: r.combo, personal: r })),
    ...publicItems.filter(r => !personalIds.has(r.combo.comboId)).map(r => ({ combo: r.combo, personal: undefined }))].slice(0, 2);
  const publicError = !loading && state.status === 'error';
  if (!loading && !personal.loading && !publicError && !personal.error && cards.length === 0) return null;
  return <section aria-label="Combo gợi ý" data-testid="product-combos"><Stack spacing="sm">
    {cards.length > 0 && <Title order={2} sx={theme => ({ fontSize: 18, color: theme.colors.blue[theme.colorScheme === 'dark' ? 3 : 7] })}>Combo gợi ý cho sản phẩm</Title>}
    {loading && <Text role="status" size="sm" color="blue">Đang tải Combo…</Text>}
    {personal.loading && <Text role="status" size="sm" color="blue">Đang đối chiếu lịch sử mua…</Text>}
    {publicError && (
      <Alert role="alert" color="red" title="Không tải được Combo">
        <Button mt="sm" variant="light" onClick={() => { setState({ productId, status: 'loading' }); setAttempt(n => n + 1); }}>Thử lại</Button>
      </Alert>
    )}
    {personal.error && <Alert color="yellow" title="Chưa tải được gợi ý cá nhân">
      <Button variant="subtle" onClick={() => setAttempt(n => n + 1)}>Thử lại gợi ý cá nhân</Button>
    </Alert>}
    {cards.map(({ combo, personal: match }) => <ComboSelection key={combo.comboId + ':' + (match?.nextItemsetOrder ?? 'context')}
      combo={combo} compact contextProductId={productId} suggestedProductIds={match?.suggestedProductIds} matchedItemsets={match?.matchedItemsets}/>)}
  </Stack></section>;
}
