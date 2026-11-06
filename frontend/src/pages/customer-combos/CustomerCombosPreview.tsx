import React, { useMemo, useState } from 'react';
import { Alert, Container, MantineProvider, NativeSelect } from '@mantine/core';
import CustomerCombos from './CustomerCombos';
import { CustomerComboDataSource } from './CustomerCombos.api';
import { CustomerCombo } from './CustomerCombos.types';
import ComboDetail from './ComboDetail';
import { fixtures, PREVIEW_COMBO_LIMIT, PREVIEW_PRODUCTS } from './CustomerCombos.fixtures';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

type Scenario = 'data' | 'loading' | 'empty' | 'error';
function createPreviewSource(scenario: Scenario): CustomerComboDataSource {
  let failed = false;
  return { async listCombos(productId, limit, signal) {
    if (scenario === 'loading') return new Promise<CustomerCombo[]>((_, reject) => {
      const abort = () => reject(new Error('Preview cancelled'));
      if (signal.aborted) abort();
      else signal.addEventListener('abort', abort, { once: true });
    });
    if (scenario === 'error' && !failed) { failed = true; throw new Error('Lỗi minh họa'); }
    if (scenario === 'empty') return [];
    return fixtures.filter(combo => combo.approved && combo.items.some(item => item.productId === productId)).slice(0, limit);
  } };
}

export default function CustomerCombosPreview() {
  const [scenario, setScenario] = useState<Scenario>('data');
  const { comboId } = useParams();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const productId = PREVIEW_PRODUCTS.find(product => product.productId === params.get('productId'))?.productId || PREVIEW_PRODUCTS[0].productId;
  const source = useMemo(() => createPreviewSource(scenario), [scenario]);
  return <MantineProvider theme={{ colorScheme: 'light', primaryColor: 'blue' }}>
    {comboId ? <ComboDetail/> : <>
      <Container size="xl" py="md">
        <Alert color="blue" mb="sm">Dữ liệu preview chỉ mang tính minh họa, không phải kết quả mining thật.</Alert>
        <NativeSelect label="Tình huống preview" value={scenario} data={[
          { value: 'data', label: 'Có dữ liệu' }, { value: 'loading', label: 'Đang tải' },
          { value: 'empty', label: 'Không có combo' }, { value: 'error', label: 'Lỗi một lần, thử lại thành công' },
        ]} onChange={event => {
          setScenario(event.currentTarget.value as Scenario);
        }}/>
      </Container>
      <CustomerCombos key={scenario} products={PREVIEW_PRODUCTS} source={source} limit={PREVIEW_COMBO_LIMIT}
        selectedProductId={productId} onProductChange={id => setParams({ productId: id })}
        onViewDetail={id => navigate(`/__preview/customer-combos/${encodeURIComponent(id)}?${new URLSearchParams({ productId })}`)}/>
    </>}
  </MantineProvider>;
}
