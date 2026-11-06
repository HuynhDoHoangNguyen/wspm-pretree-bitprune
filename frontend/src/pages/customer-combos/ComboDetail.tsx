import React, { useEffect, useState } from 'react';
import { Alert, Anchor, Badge, Breadcrumbs, Button, Card, Container, Group, NativeSelect, Skeleton, Stack, Text, Title } from '@mantine/core';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { CustomerCombo } from './CustomerCombos.types';
import { formatPrice } from './ProductSelectionCard';
import { Package } from 'tabler-icons-react';
import { CustomerStoreLayout } from './CustomerCombos';
import { fixtures } from './CustomerCombos.fixtures';
import './CustomerCombos.css';

export default function ComboDetail() {
  const { comboId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const productId = params.get('productId');
  const backUrl = '/__preview/customer-combos' + (productId ? `?${new URLSearchParams({ productId })}` : '');
  const scenario = params.get('detailState') || 'data';
  return <CustomerStoreLayout>
    <Container size="xl" py="xl"><Stack spacing="lg">
      <Breadcrumbs aria-label="Điều hướng chi tiết combo">
        <Anchor component={Link} to={backUrl}>Sản phẩm</Anchor>
        <Anchor component={Link} to={backUrl}>Combo gợi ý</Anchor>
        <Text>Chi tiết Combo</Text>
      </Breadcrumbs>
      <Group><Button component={Link} to={backUrl} variant="light" color="blue" radius="md">← Quay lại danh sách Combo</Button></Group>
      <Alert color="blue">Dữ liệu preview chỉ mang tính minh họa, không phải kết quả mining thật.</Alert>
      <NativeSelect label="Tình huống chi tiết preview" value={scenario} data={[
        { value: 'data', label: 'Có dữ liệu' }, { value: 'loading', label: 'Đang tải' },
        { value: 'error', label: 'Lỗi một lần, thử lại thành công' },
      ]} onChange={event => {
        const next = new URLSearchParams(params);
        next.set('detailState', event.currentTarget.value);
        setParams(next);
      }}/>
      <DetailResult key={`${comboId}:${scenario}`} comboId={comboId} scenario={scenario}/>
    </Stack></Container>
  </CustomerStoreLayout>;
}

function DetailResult({ comboId, scenario }: { comboId: string; scenario: string }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'success'; combo: CustomerCombo | null }>({ status: 'loading', combo: null });
  useEffect(() => {
    let active = true;
    // Async UI simulation only: no network, no ranking, no mining calculations.
    if (scenario === 'loading') return;
    Promise.resolve().then(() => {
      if (!active) return;
      if (scenario === 'error' && attempt === 0) setState({ status: 'error', combo: null });
      else setState({ status: 'success', combo: fixtures.find(combo => combo.comboId === comboId && combo.approved) || null });
    });
    return () => { active = false; };
  }, [comboId, scenario, attempt]);
  if (state.status === 'loading') return <div role="status" aria-label="Đang tải chi tiết combo"><Text mb="md">Đang tải chi tiết combo…</Text><Skeleton height={320} radius="lg"/></div>;
  if (state.status === 'error') return <Alert color="red" title="Không thể tải chi tiết combo">
    <Text mb="sm">Đã xảy ra lỗi minh họa. Vui lòng thử lại.</Text>
    <Button onClick={() => { setState({ status: 'loading', combo: null }); setAttempt(value => value + 1); }}>Thử lại</Button>
  </Alert>;
  if (!state.combo) return <Alert color="blue" title="Không tìm thấy combo">Combo không tồn tại hoặc chưa đủ điều kiện hiển thị.</Alert>;
  return <ComboDetailContent combo={state.combo}/>;
}

/** Presentational fixture content. No temporal structure is invented for the flat fixture. */
export function ComboDetailContent({ combo }: {
  combo: CustomerCombo;
}) {
  const hasDiscount = Number.isFinite(combo.referencePrice) && Number.isFinite(combo.price) &&
    combo.price >= 0 && (combo.referencePrice as number) > combo.price;

  return <Stack spacing="lg">
    <div>
      <Badge color="blue" variant="light">Combo · {combo.items.length} sản phẩm</Badge>
      <Title order={1} mt="sm">{combo.name}</Title>
      <Text color="dimmed" size="sm" mt="xs">Mã combo: {combo.comboId}</Text>
    </div>
    <div className="customer-detail-layout">
      <section aria-label="Sản phẩm trong combo">
        <Title order={2} mb="md">Sản phẩm trong combo</Title>
        <Stack spacing="sm">
          {combo.items.map(product => <Card withBorder radius="md" p="md" key={product.productId}>
            <Group noWrap align="flex-start">
              <div role="img" aria-label={`Ảnh minh họa ${product.name}`} style={{ color: '#1971c2', flexShrink: 0 }}>
                <Package size={40} strokeWidth={1}/>
              </div>
              <Stack spacing={4} style={{ minWidth: 0 }}>
                <Text weight={600}>{product.name}</Text>
                <Text color="dimmed" size="sm">Mã sản phẩm: {product.code} · {product.category}</Text>
                <Text color="blue" weight={600}>Giá sản phẩm: {formatPrice(product.price)}</Text>
              </Stack>
            </Group>
          </Card>)}
        </Stack>
      </section>
      <Card role="region" aria-label="Giá Combo" withBorder radius="lg" p="xl" className="customer-detail-summary">
        <Stack spacing="xs">
          {hasDiscount && <Text color="dimmed" size="sm">Giá tham chiếu: <s>{formatPrice(combo.referencePrice as number)}</s></Text>}
          <Text color="blue" size="xl" weight={700}>Giá combo: {formatPrice(combo.price)}</Text>
          {hasDiscount && <Text color="blue">Tiết kiệm {formatPrice((combo.referencePrice as number) - combo.price)}</Text>}
          {hasDiscount && <Badge color="blue" variant="light">Giảm {new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(((combo.referencePrice as number) - combo.price) / (combo.referencePrice as number) * 100)}%</Badge>}
          <Text color="dimmed" size="xs">Giá minh họa, chưa phải báo giá mua hàng.</Text>
        </Stack>
      </Card>
    </div>
    {(combo.supportCount !== undefined || combo.patternWeight !== undefined) && <Stack spacing={4}>
      {combo.supportCount !== undefined && <Text size="sm">Số chuỗi hỗ trợ (minh họa): {combo.supportCount}</Text>}
      {combo.patternWeight !== undefined && <Text size="sm">Trọng số mẫu (minh họa): {combo.patternWeight}</Text>}
    </Stack>}
  </Stack>;
}
