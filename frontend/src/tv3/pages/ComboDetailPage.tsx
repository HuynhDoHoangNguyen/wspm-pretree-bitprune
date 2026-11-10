import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Anchor, Badge, Button, Card, Container, Group, Image, Stack, Text, Title } from '@mantine/core';
import { ComboApiError, displayMoney, isComboIdentifier, readPublicCombo } from '../api/combos';
import { ComboPricing } from '../components/ComboCard';
import { PublicCombo } from '../models/combos';

interface Props { comboId: string; productId?: string | null }
type State = { comboId: string } & (
  | { status: 'loading' | 'error' | 'not-found' }
  | { status: 'success'; data: PublicCombo }
);

export default function ComboDetailPage({ comboId, productId }: Props) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<State>({ comboId, status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ comboId, status: 'loading' });
    const load = async () => {
      try {
        const data = await readPublicCombo(comboId, controller.signal);
        if (active) setState({ comboId, status: 'success', data });
      } catch (error) {
        if (active && !controller.signal.aborted) {
          setState({ comboId, status: error instanceof ComboApiError && error.status === 404 ? 'not-found' : 'error' });
        }
      }
    };
    void load();
    return () => { active = false; controller.abort(); };
  }, [comboId, attempt]);

  const loading = state.comboId !== comboId || state.status === 'loading';
  const combo = !loading && state.status === 'success' ? state.data : null;
  const groups = new Map<number, PublicCombo['items']>();
  combo?.items.forEach(item => groups.set(item.itemsetOrder, [...(groups.get(item.itemsetOrder) ?? []), item]));
  const backHref = productId && isComboIdentifier(productId) ? '/products/' + encodeURIComponent(productId) : '/products';
  return <Container size="xl" py="xl" role="region" aria-label="Chi tiết Combo">
    <Stack spacing="lg" sx={{ overflowWrap: 'anywhere' }}>
      <Anchor component={Link} to={backHref}>Về sản phẩm</Anchor>
      {loading && <Text role="status">Đang tải chi tiết Combo…</Text>}
      {!loading && state.status === 'not-found' && <Text role="status">Không tìm thấy Combo công khai.</Text>}
      {!loading && state.status === 'error' && <Alert role="alert" color="red" title="Không tải được chi tiết Combo">
        <Button mt="sm" variant="light" onClick={() => { setState({ comboId, status: 'loading' }); setAttempt(n => n + 1); }}>Thử lại</Button>
      </Alert>}
      {combo && <>
        <Title order={1}>{combo.name}</Title>
        <Text>Mã Combo: {combo.comboId}</Text>
        <Group><Badge>Mua cùng một lần</Badge><Badge color="green">Đã công bố</Badge></Group>
        {combo.explanation && <Text>{combo.explanation}</Text>}
        {Array.from(groups.entries()).map(([order, items]) => <Stack key={order} spacing="sm">
          <Title order={2}>Nhóm sản phẩm {order}</Title>
          {items.map(item => <Card key={item.itemOrder} withBorder radius="md">
            <Group align="flex-start" noWrap>
              <Image src={item.product.imageUrl || undefined} alt={item.product.name} width={64} height={64} fit="contain" withPlaceholder/>
              <Stack spacing="xs" sx={{ minWidth: 0 }}>
                <Anchor component={Link} to={'/products/' + encodeURIComponent(item.product.productId)}>{item.product.name}</Anchor>
                <Text size="sm">Mã sản phẩm: {item.product.productId} · Số lượng: {item.quantity}</Text>
                <Text size="sm">Đơn giá tham chiếu: {displayMoney(item.unitPriceSnapshot, combo.pricing.currencyCode)}</Text>
              </Stack>
            </Group>
          </Card>)}
        </Stack>)}
        <Card withBorder radius="md"><ComboPricing combo={combo}/></Card>
        <Text>Số chuỗi hỗ trợ: {combo.metrics.supportAbs}/{combo.metrics.sequenceCount} · Tỷ lệ hỗ trợ: {combo.metrics.supportRatio} · Trọng số mẫu: {combo.metrics.patternWeight}</Text>
        <Text size="sm" color="dimmed">Batch: {combo.provenance.batch.batchId} · Run: {combo.provenance.batch.runId} · Snapshot: {combo.provenance.batch.snapshotId}</Text>
        <Text size="sm" color="dimmed">Thuật toán: {combo.provenance.batch.algorithm} · Ranking: {combo.provenance.batch.rankingPolicy} · Weight policy: {combo.provenance.batch.patternWeightPolicy}</Text>
        <Text size="sm" color="dimmed">Mẫu: {combo.provenance.primaryPatternId} · {combo.provenance.canonicalPattern}</Text>
      </>}
    </Stack>
  </Container>;
}
