import React, { useState } from 'react';
import { Alert, Button, Stack, Text, Title } from '@mantine/core';
import usePersonalCombos from './usePersonalCombos';
import ComboSelection from './ComboSelection';

export default function PersonalRecommendations({ productId }: { productId?: string }) {
  const [attempt, setAttempt] = useState(0);
  const { authenticated, loading, items, error } = usePersonalCombos(productId, attempt);
  if (!authenticated || (!loading && !error && items.length === 0)) return null;
  return <section aria-label="Gợi ý theo lịch sử mua"><Stack spacing="sm">
    <Title order={2} sx={theme => ({ fontSize: 20, color: theme.colors.blue[theme.colorScheme === 'dark' ? 3 : 7] })}>Gợi ý cho lần mua tiếp theo</Title>
    {error ? <Alert color="yellow" title="Chưa tải được gợi ý cá nhân">
      <Button variant="subtle" onClick={() => setAttempt(n => n + 1)}>Thử lại gợi ý cá nhân</Button>
    </Alert> : loading ? <Text role="status">Đang đối chiếu lịch sử mua…</Text> : items.map(r => <ComboSelection key={r.combo.comboId + ':' + r.nextItemsetOrder} combo={r.combo}
      matchedItemsets={r.matchedItemsets} suggestedProductIds={r.suggestedProductIds} contextProductId={productId}/>)}
  </Stack></section>;
}
