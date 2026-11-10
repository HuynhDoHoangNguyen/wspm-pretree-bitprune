import React from 'react';
import { act, fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { authSession } from 'auth/session';
import { personalCombos, latestPublicCombos, listPublicCombos } from 'api/recommendation/combos';
import { getProducts } from 'api/catalog/catalog';
import { PublicCombo, PersonalCombos } from 'models/recommendation/combos';
import ComboSelection, { selectedTotal } from './ComboSelection';
import PersonalRecommendations from './PersonalRecommendations';
import RelatedProducts from './RelatedProducts';
import LatestCombos from './LatestCombos';
import ProductComboSection from 'components/recommendation/ProductComboSection';

jest.mock('api/recommendation/combos', () => ({ ...jest.requireActual('api/recommendation/combos'), personalCombos: jest.fn(), latestPublicCombos: jest.fn(), listPublicCombos: jest.fn() }));
jest.mock('api/catalog/catalog', () => ({ getProducts: jest.fn() }));
const personal = personalCombos as jest.MockedFunction<typeof personalCombos>;
const products = getProducts as jest.MockedFunction<typeof getProducts>;
const combo = {
  comboId: 'test', name: 'Mouse then speaker', kind: 'SEQUENTIAL_CROSS_SELL', status: 'PUBLISHED',
  pricing: { currencyCode: 'VND' },
  items: ['Mouse', 'Speaker'].map((name, index) => ({ itemsetOrder: index + 1, itemOrder: 1, quantity: 2,
    product: { productId: name, name, imageUrl: null, currentPrice: index ? '0.20' : '9007199254740993.99', availableQuantity: '10' } })),
} as PublicCombo;
beforeEach(() => { personal.mockReset(); products.mockReset(); authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null }); });
afterEach(() => authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null }));

test('checkbox totals use exact decimal prices and quantities without applying a combo discount', () => {
  expect(selectedTotal(combo.items)).toBe('18014398509481988.38');
  render(<ComboSelection combo={{ ...combo, kind: 'SAME_ORDER_BUNDLE' }}/>);
  expect(screen.getByText(/Tổng giá đang chọn: 0 VND/)).toBeVisible();
  fireEvent.click(screen.getByRole('checkbox', { name: 'Chọn Mouse' }));
  fireEvent.change(screen.getByLabelText('Số lượng Mouse'), { target: { value: '2' } });
  fireEvent.click(screen.getByRole('checkbox', { name: 'Chọn Speaker' }));
  fireEvent.change(screen.getByLabelText('Số lượng Speaker'), { target: { value: '2' } });
  expect(screen.getByText(/18\.014\.398\.509\.481\.988,38 VND/)).toBeVisible();
  fireEvent.click(screen.getByRole('checkbox', { name: 'Chọn Mouse' }));
  expect(screen.getByText(/Tổng giá đang chọn: 0,4 VND/)).toBeVisible();
});

test('changing product context clears selected rows and quantities',()=>{
  const bundle={...combo,kind:'SAME_ORDER_BUNDLE'} as PublicCombo;
  const view=render(<ComboSelection combo={bundle} contextProductId="Mouse"/>);
  fireEvent.click(screen.getByRole('checkbox',{name:'Chọn Speaker'}));
  fireEvent.change(screen.getByLabelText('Số lượng Speaker'),{target:{value:'2'}});
  view.rerender(<ComboSelection combo={bundle} contextProductId="Speaker"/>);
  expect(screen.getByRole('checkbox',{name:'Chọn Speaker'})).not.toBeChecked();
  expect(screen.getByLabelText('Số lượng Speaker')).toHaveValue(1);
  expect(screen.getByText(/Tổng giá đang chọn: 0 VND/)).toBeVisible();
});

test('personal cards show only next-step products and discard held responses after logout', async () => {
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'test-token' });
  let resolve!: (value: PersonalCombos) => void;
  personal.mockReturnValue(new Promise(r => { resolve = r; }));
  const view = render(<PersonalRecommendations/>);
  expect(personal).toHaveBeenCalledWith('test-token', expect.any(AbortSignal));
  act(() => authSession.unauthorized());
  await act(async () => resolve({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: '', items: [{ rank: 1, matchedItemsets: 1, nextItemsetOrder: 2, suggestedProductIds: ['Speaker'], combo }] }));
  expect(screen.queryByText('Mouse then speaker')).not.toBeInTheDocument();
  act(() => authSession.useStore.setState({ status: 'authenticated', accessToken: 'different-token' }));
  personal.mockResolvedValue({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: '', items: [{ rank: 1, matchedItemsets: 1, nextItemsetOrder: 2, suggestedProductIds: ['Speaker'], combo }] });
  view.unmount();
  render(<PersonalRecommendations/>);
  expect(await screen.findByRole('checkbox', { name: 'Chọn Speaker' })).toBeVisible();
  expect(screen.queryByRole('checkbox', { name: 'Chọn Mouse' })).not.toBeInTheDocument();
});

test('related products exclude current, other categories and inactive products', async () => {
  const base = { productName: 'Product', categoryId: 'cat', productStatus: 'ACTIVE', price: 1, imageUrl: null, createdAt: null };
  products.mockResolvedValue([{ ...base, productId: 'current' }, { ...base, productId: 'related', productName: 'Related' },
    { ...base, productId: 'inactive', productStatus: 'INACTIVE' }, { ...base, productId: 'other', categoryId: 'other' }]);
  render(<RelatedProducts productId="current" categoryId="cat"/>);
  const section = screen.getByRole('region', { name: 'Sản phẩm liên quan' });
  expect(await within(section).findByRole('link', { name: 'Xem sản phẩm Related' })).toHaveAttribute('href', '/products/related');
  expect(within(section).getAllByRole('article')).toHaveLength(1);
});

test('all successful empty sections disappear without headings or empty messages', async () => {
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'test-token' });
  personal.mockResolvedValue({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: '', items: [] });
  (latestPublicCombos as jest.Mock).mockResolvedValue([]);
  (listPublicCombos as jest.Mock).mockResolvedValue({ items: [] });
  const view = render(<><PersonalRecommendations/><LatestCombos/><ProductComboSection productId="Mouse"/></>);
  await waitFor(() => expect(view.container).toBeEmptyDOMElement());
});

test('detail prioritizes personal next step, removes duplicate public combo and caps total cards at two', async () => {
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'test-token' });
  personal.mockResolvedValue({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: '', items: [
    { rank: 1, matchedItemsets: 1, nextItemsetOrder: 2, suggestedProductIds: ['Speaker'], combo }] });
  (listPublicCombos as jest.Mock).mockResolvedValue({ items: [
    { rank: 1, combo }, { rank: 2, combo: { ...combo, kind: 'SAME_ORDER_BUNDLE', comboId: 'second', name: 'Second' } },
    { rank: 3, combo: { ...combo, kind: 'SAME_ORDER_BUNDLE', comboId: 'third', name: 'Third' } }] });
  render(<ProductComboSection productId="Mouse"/>);
  await screen.findByText(/Dựa trên lịch sử mua/);
  expect(screen.getAllByRole('heading', { level: 3 }).map(h => h.textContent)).toEqual(['Mouse then speaker', 'Second']);
  expect(screen.queryByText('Third')).not.toBeInTheDocument();
  const personalCard = screen.getAllByRole('article')[0];
  expect(within(personalCard).getByRole('checkbox', { name: 'Chọn Speaker' })).toBeVisible();
  expect(within(personalCard).queryByRole('checkbox', { name: 'Chọn Mouse' })).not.toBeInTheDocument();
});

test('mouse-to-speaker suggestion appears on mouse detail but never suggests the speaker to itself', async () => {
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'test-token' });
  personal.mockResolvedValue({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: '', items: [
    { rank: 1, matchedItemsets: 1, nextItemsetOrder: 2, suggestedProductIds: ['Speaker'], combo }] });
  (listPublicCombos as jest.Mock).mockResolvedValue({ items: [] });
  const view = render(<ProductComboSection productId="Mouse"/>);
  expect(await screen.findByRole('checkbox', { name: 'Chọn Speaker' })).toBeVisible();
  view.rerender(<ProductComboSection productId="Speaker"/>);
  await waitFor(() => expect(personal).toHaveBeenLastCalledWith('test-token', expect.any(AbortSignal), 'Speaker'));
  await waitFor(() => expect(view.container).toBeEmptyDOMElement());
  view.unmount();
  render(<PersonalRecommendations/>);
  expect(await screen.findByRole('checkbox', { name: 'Chọn Speaker' })).toBeVisible();
});

test('latest card includes names, prices and images of constituent products; failure remains retryable', async () => {
  (latestPublicCombos as jest.Mock).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([{ ...combo, kind: 'SAME_ORDER_BUNDLE' }]);
  render(<LatestCombos/>);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại combo mới nhất' }));
  const card = await screen.findByRole('article');
  expect(within(card).getByText('Speaker')).toBeVisible();
  expect(within(card).getByText('0,2 VND × 2')).toBeVisible();
  expect(within(card).getAllByRole('img').length).toBeGreaterThan(0);
});

test('latest shows five itemset combos without duplicate linked titles or kind badges', async () => {
  (latestPublicCombos as jest.Mock).mockResolvedValue(Array.from({ length: 5 }, (_, i) => ({
    ...combo, kind: 'SAME_ORDER_BUNDLE', comboId: 'latest-' + i, name: 'Duplicate combo title ' + i,
  })));
  render(<LatestCombos/>);
  const section = screen.getByLabelText('Top Combo mới nhất');
  await waitFor(() => expect(within(section).getAllByRole('article')).toHaveLength(5));
  expect(within(section).queryByText(/Duplicate combo title/)).not.toBeInTheDocument();
  expect(within(section).queryByText(/Mua cùng đơn/i)).not.toBeInTheDocument();
  expect(within(section).getAllByRole('link', { name: 'Xem chi tiết Combo' })).toHaveLength(5);
  for(let rank=1;rank<=5;rank++)expect(within(section).getByText('#'+rank,{exact:false})).toBeVisible();
  expect(within(section).getAllByText('Speaker')).toHaveLength(5);
});
