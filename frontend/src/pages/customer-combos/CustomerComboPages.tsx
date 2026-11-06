import React, { useEffect, useState } from 'react';
import { Alert, Anchor, Badge, Button, Card, Container, Group, Image, NativeSelect, Stack, Text, Title } from '@mantine/core';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { CatalogProduct, loadCatalog } from 'pages/client-home/CustomerCatalog.api';
import ProductSelectionCard from './ProductSelectionCard';
import { ComboApiError, displayMoney, listPublicCombos, PublicCombo, PublicTopK, readPublicCombo } from './CustomerComboRead.api';
import './CustomerCombos.css';

type Load<T> = { status: 'loading' } | { status: 'error'; missing: boolean } | { status: 'success'; data: T };
const kindLabels: Record<string, string> = { SAME_ORDER_BUNDLE: 'Mua cùng một lần', SEQUENTIAL_CROSS_SELL: 'Gợi ý theo trình tự mua', MIXED_SEQUENCE: 'Gợi ý theo nhóm và trình tự' };
const discountLabels: Record<string, string> = { PERCENTAGE: 'Tỷ lệ giảm trên sản phẩm đủ điều kiện', FIXED_AMOUNT: 'Mức giảm trên sản phẩm đủ điều kiện', FIXED_PRICE: 'Giá cố định cho phần sản phẩm đủ điều kiện' };
function Frame({ children }: { children: React.ReactNode }) {
  return <Container size="xl" py="xl"><Stack spacing="lg">
    <Anchor component={Link} to="/">← Sản phẩm</Anchor>{children}
  </Stack></Container>;
}
function Failure({ missing, retry }: { missing: boolean; retry: () => void }) {
  return <Alert role="alert" color={missing ? 'blue' : 'red'} title={missing ? 'Không tìm thấy nội dung công khai' : 'Không tải được dữ liệu Combo'}>
    <Text mb="sm">{missing ? 'Nội dung không tồn tại hoặc hiện chưa được công bố.' : 'Vui lòng thử lại sau.'}</Text>
    {!missing && <Button color="blue" variant="light" onClick={retry}>Thử lại</Button>}
  </Alert>;
}
function Prices({ combo }: { combo: PublicCombo }) {
  const p = combo.pricing;
  return <Stack spacing="xs">
    <Text color="dimmed">Giá tham chiếu: {displayMoney(p.referenceOriginalPrice, p.currencyCode)}</Text>
    <Text color="blue" weight={700} size="xl">Giá Combo: {displayMoney(p.referenceSalePrice, p.currencyCode)}</Text>
    {p.discountType !== 'NONE' && p.discountValue !== null && <Text size="sm">{discountLabels[p.discountType]}: {p.discountType === 'PERCENTAGE'
      ? displayMoney(p.discountValue, '%')
      : displayMoney(p.discountValue, p.currencyCode)}</Text>}
    {p.maxDiscountAmount !== null && <Text size="sm">Giảm tối đa: {displayMoney(p.maxDiscountAmount, p.currencyCode)}</Text>}
    <Text size="xs" color="dimmed">Giá tham chiếu lúc công bố, không phải báo giá thanh toán.</Text>
  </Stack>;
}
export function CustomerComboListPage() {
  const { productId = '' } = useParams();
  const navigate = useNavigate();
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<Load<{ products: CatalogProduct[]; topK: PublicTopK }>>({ status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    Promise.all([loadCatalog(controller.signal), listPublicCombos(productId, controller.signal)]).then(([products, topK]) => {
      if (!controller.signal.aborted) setState({ status: 'success', data: { products, topK } });
    }).catch(error => {
      if (!controller.signal.aborted) setState({ status: 'error', missing: error instanceof ComboApiError && error.status === 404 });
    });
    return () => controller.abort();
  }, [productId, attempt]);
  const product = state.status === 'success' ? state.data.products.find(p => p.productId === productId) : undefined;
  return <Frame><Title order={1}>Combo dành cho sản phẩm</Title>
    {state.status === 'loading' && <Text role="status">Đang tải Combo…</Text>}
    {state.status === 'error' && <Failure missing={state.missing} retry={() => setAttempt(n => n + 1)}/>}
    {state.status === 'success' && (product ? <div className="customer-combos-layout">
      <section><NativeSelect label="Sản phẩm" value={productId} mb="md" data={state.data.products.map(p => ({ value: p.productId, label: p.productName }))}
        onChange={event => navigate('/products/' + encodeURIComponent(event.currentTarget.value) + '/combos')}/>
      <ProductSelectionCard product={{ productId, name: product.productName, code: productId, category: product.categoryId || '', price: product.price, imageUrl: product.imageUrl }}/>
      </section>
      <section><Text mb="md">Tối đa {state.data.topK.effectiveK} Combo · Thứ tự do hệ thống cung cấp</Text>
        {!state.data.topK.items.length ? <Alert color="blue" role="status">Hiện chưa có combo được công bố phù hợp với sản phẩm này.</Alert>
          : <div className="customer-combo-grid">{state.data.topK.items.map(({ rank, combo }) => <Card key={combo.comboId} component="article" withBorder radius="lg" className="customer-combo">
            <Stack><Badge color="blue">Gợi ý {rank}</Badge><Title order={2}>{combo.name}</Title>
              <Text>{combo.explanation}</Text><ul className="customer-combo-items">{combo.items.map(i => <li key={i.itemsetOrder + ':' + i.itemOrder}>
                <Group noWrap spacing="xs" py={4}>
                  {i.product.imageUrl && <Image src={i.product.imageUrl} alt={i.product.name} width={48} height={48} fit="contain" withPlaceholder/>}
                  <Text size="sm" style={{ minWidth: 0 }}>{i.product.name} × {i.quantity}</Text>
                </Group></li>)}</ul>
              <Text size="sm">Số chuỗi hỗ trợ: {combo.metrics.supportAbs}/{combo.metrics.sequenceCount} · Trọng số mẫu: {combo.metrics.patternWeight}</Text>
              <Prices combo={combo}/><Button component={Link} color="blue" variant="light"
                to={'/combos/' + encodeURIComponent(combo.comboId) + '?' + new URLSearchParams({ productId })}>Xem chi tiết</Button>
            </Stack></Card>)}</div>}
      </section></div> : <Failure missing retry={() => undefined}/>)}
  </Frame>;
}
export function CustomerComboDetailPage() {
  const { comboId = '' } = useParams();
  const [params] = useSearchParams();
  const productId = params.get('productId');
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<Load<PublicCombo>>({ status: 'loading' });
  useEffect(() => {
    const controller = new AbortController(); setState({ status: 'loading' });
    readPublicCombo(comboId, controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ status: 'success', data });
    }).catch(error => {
      if (!controller.signal.aborted) setState({ status: 'error', missing: error instanceof ComboApiError && error.status === 404 });
    });
    return () => controller.abort();
  }, [comboId, attempt]);
  const groups = new Map<number, PublicCombo['items']>();
  const detail = state.status === 'success' ? state.data : null;
  if (detail) detail.items.forEach(item => groups.set(item.itemsetOrder, [...(groups.get(item.itemsetOrder) || []), item]));
  return <Frame>{productId && <Anchor component={Link} to={'/products/' + encodeURIComponent(productId) + '/combos'}>← Combo của sản phẩm đã chọn</Anchor>}
    {state.status === 'loading' && <Text role="status">Đang tải chi tiết Combo…</Text>}
    {state.status === 'error' && <Failure missing={state.missing} retry={() => setAttempt(n => n + 1)}/>}
    {detail && <><Title order={1}>{detail.name}</Title><Text>{detail.explanation}</Text><Badge color="blue">{kindLabels[detail.kind]}</Badge>
      <div className="customer-detail-layout"><section><Stack>{Array.from(groups.entries()).map(([order, items]) => <Stack key={order}>
        <Title order={2}>Nhóm sản phẩm {order}</Title>{items.map(item => <Card withBorder radius="md" key={item.itemOrder}>
          <Group align="flex-start" noWrap><Image width={80} height={80} fit="contain" src={item.product.imageUrl || undefined} alt={item.product.name} withPlaceholder/>
            <Stack spacing={4}><Text weight={600}>{item.product.name}</Text><Text>Số lượng: {item.quantity}</Text>
              <Text>Đơn giá tham chiếu: {displayMoney(item.unitPriceSnapshot, detail.pricing.currencyCode)}</Text>
            </Stack></Group></Card>)}</Stack>)}</Stack></section>
      <Card withBorder radius="lg" className="customer-detail-summary"><Prices combo={detail}/></Card></div>
      <Text size="sm">Số chuỗi hỗ trợ: {detail.metrics.supportAbs}/{detail.metrics.sequenceCount} · Trọng số mẫu: {detail.metrics.patternWeight}</Text>
    </>}
  </Frame>;
}
