import React, { useEffect, useState } from 'react';
import { Alert, Anchor, Button, Container, Group, MantineProvider, NativeSelect, SegmentedControl, Skeleton, Stack, Text, Title, useMantineColorScheme } from '@mantine/core';
import { Moon, Package, Sun } from 'tabler-icons-react';
import { Link } from 'react-router-dom';
import { BRAND_NAME } from 'constants/Brand';
import { CustomerComboDataSource } from './CustomerCombos.api';
import { CustomerCombo, CustomerProduct } from './CustomerCombos.types';
import ProductSelectionCard from './ProductSelectionCard';
import ComboRecommendationCard from './ComboRecommendationCard';
import './CustomerCombos.css';

// Mantine 4 recreates its global Emotion cache when inherited options have no key.
const CUSTOMER_EMOTION_OPTIONS = { key: 'mantine', prepend: true };

interface Props {
  products: CustomerProduct[];
  source: CustomerComboDataSource;
  limit: number;
  onViewDetail?: (comboId: string) => void;
  selectedProductId?: string;
  onProductChange?: (productId: string) => void;
}

export default function CustomerCombos({ products, source, limit, onViewDetail, selectedProductId, onProductChange }: Props) {
  const [selectedId, setSelectedId] = useState(products[0]?.productId || '');
  const product = products.find(item => item.productId === (selectedProductId ?? selectedId)) || products[0];
  const safeLimit = Number.isFinite(limit) ? Math.max(0, Math.floor(limit)) : 0;
  return (
    <CustomerStoreLayout>
      <Container size="xl" py="xl">
        <Stack spacing="xl">
          <div><Text color="blue" weight={600} size="sm">KHÁM PHÁ & KẾT HỢP</Text>
            <Title order={1} mt="xs">Sản phẩm bạn chọn. Combo bạn cần.</Title>
            <Text color="dimmed" mt="sm">Tìm một sản phẩm và xem các bộ sản phẩm gợi ý đi kèm.</Text>
          </div>
          {product ? <div className="customer-combos-layout">
            <section aria-label="Chọn sản phẩm">
              <NativeSelect label="Sản phẩm" value={product.productId} mb="md" radius="md"
                data={products.map(item => ({ value: item.productId, label: item.name }))}
                onChange={event => {
                  setSelectedId(event.currentTarget.value);
                  onProductChange?.(event.currentTarget.value);
                }}/>
              <ProductSelectionCard product={product}/>
            </section>
            <section aria-label="Combo gợi ý">
              <Title order={2}>Combo dành cho sản phẩm này</Title>
              <Text size="sm" color="dimmed" mt="xs" mb="lg">Tối đa {safeLimit} combo · Giữ nguyên thứ tự từ nguồn dữ liệu</Text>
              <ComboResults key={`${product.productId}:${safeLimit}`} productId={product.productId}
                source={source} limit={safeLimit} onViewDetail={onViewDetail}/>
            </section>
          </div> : <Alert color="blue">Chưa có sản phẩm để lựa chọn.</Alert>}
        </Stack>
      </Container>
    </CustomerStoreLayout>
  );
}

export function CustomerStoreLayout({ children, actions }: { children: React.ReactNode; actions?: React.ReactNode }) {
  const { colorScheme } = useMantineColorScheme();
  return <MantineProvider inherit emotionOptions={CUSTOMER_EMOTION_OPTIONS} theme={{ colorScheme, primaryColor: 'blue' }}><div className="customer-combos-page" data-testid="customer-store-layout" data-color-scheme={colorScheme}>
    <CustomerStoreHeader actions={actions}/>
    <main>{children}</main>
    <CustomerStoreFooter/>
  </div></MantineProvider>;
}

export function CustomerStoreHeader({ actions }: { actions?: React.ReactNode }) {
  return <header className="customer-store-header">
    <Container size="xl"><Group position="apart"><Group spacing="sm"><Package color="#1971c2"/>
      <Anchor component={Link} to="/" weight={800} size="xl">{BRAND_NAME}</Anchor><Text color="dimmed" size="sm">Không gian combo của bạn</Text>
    </Group>{actions}</Group></Container>
  </header>;
}

export function CustomerStoreFooter() {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  return <footer className="customer-store-footer"><Container size="xl">
    <div className="customer-store-footer-grid">
      <Anchor component={Link} to="/" weight={800} size="xl">{BRAND_NAME}</Anchor>
      <Stack spacing={6}><Text weight={600}>Tổng đài hỗ trợ:</Text><Text>(024) 3535 7272, (028) 35 111 222</Text></Stack>
      <Stack spacing={6}><Text weight={600}>Địa chỉ liên hệ:</Text><Text>Tòa nhà Bitexco, Quận 1, Thành phố Hồ Chí Minh</Text></Stack>
    </div>
    <Group position="apart" className="customer-store-footer-bottom">
      <Text size="sm">© 2026 {BRAND_NAME}. Bảo lưu mọi quyền.</Text>
      <SegmentedControl aria-label="Chế độ màu" color="blue" value={colorScheme}
        onChange={value => toggleColorScheme(value as 'light' | 'dark')} data={[
          { value: 'light', label: <Group spacing={6} noWrap><Sun size={16} aria-hidden="true"/><span>Sáng</span></Group> },
          { value: 'dark', label: <Group spacing={6} noWrap><Moon size={16} aria-hidden="true"/><span>Tối</span></Group> },
        ]}/>
    </Group>
  </Container></footer>;
}

function ComboResults({ productId, source, limit, onViewDetail }: {
  productId: string; source: CustomerComboDataSource; limit: number;
  onViewDetail?: (comboId: string) => void;
}) {
  const [state, setState] = useState<{ status: 'loading' | 'success' | 'error'; combos: CustomerCombo[] }>({ status: 'loading', combos: [] });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading', combos: [] });
    Promise.resolve().then(() => source.listCombos(productId, limit, controller.signal)).then(combos => {
      if (!controller.signal.aborted) setState({ status: 'success', combos: combos.slice(0, limit) });
    }).catch(() => {
      if (!controller.signal.aborted) setState({ status: 'error', combos: [] });
    });
    return () => controller.abort();
  }, [productId, source, limit, attempt]);

  if (state.status === 'loading') return <div role="status" aria-label="Đang tải combo">
    <Text mb="sm">Đang tải combo…</Text><div className="customer-combo-grid">
      {[0, 1].map(index => <Skeleton key={index} height={300} radius="lg"/>)}</div></div>;
  if (state.status === 'error') return <Alert color="red" title="Không thể tải combo" role="alert">
    <Text size="sm" mb="sm">Vui lòng thử lại để xem gợi ý.</Text>
    <Button color="blue" onClick={() => { setState({ status: 'loading', combos: [] }); setAttempt(value => value + 1); }}>Thử lại</Button>
  </Alert>;
  if (!state.combos.length) return <Alert color="blue" title="Chưa có combo gợi ý">Hãy chọn sản phẩm khác để tiếp tục khám phá.</Alert>;
  return <div className="customer-combo-grid">{state.combos.map(combo => (
    <ComboRecommendationCard key={combo.comboId} combo={combo} onViewDetail={onViewDetail}/>
  ))}</div>;
}
