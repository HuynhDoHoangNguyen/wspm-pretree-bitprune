import React from 'react';
import { act, fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { authSession } from 'auth/session';
import { personalCombos, latestPublicCombos, listPublicCombos } from 'tv3/api/combos';
import { getProducts } from 'tv3/api/catalog';
import { PublicCombo, PersonalCombos } from 'tv3/models/combos';
import ComboSelection, { selectedTotal } from './ComboSelection';
import PersonalRecommendations from './PersonalRecommendations';
import RelatedProducts from './RelatedProducts';
import LatestCombos from './LatestCombos';
import ProductComboSection from 'tv3/components/ProductComboSection';

jest.mock('tv3/api/combos', () => ({ ...jest.requireActual('tv3/api/combos'), personalCombos: jest.fn(), latestPublicCombos: jest.fn(), listPublicCombos: jest.fn() }));
jest.mock('tv3/api/catalog', () => ({ getProducts: jest.fn() }));
const personal = personalCombos as jest.MockedFunction<typeof personalCombos>;
const products = getProducts as jest.MockedFunction<typeof getProducts>;
const combo = {
  comboId: 'test', name: 'Mouse then speaker', kind: 'SEQUENTIAL_CROSS_SELL', status: 'PUBLISHED',
  pricing: { currencyCode: 'VND' },
  items: ['Mouse', 'Speaker'].map((name, index) => ({ itemsetOrder: index + 1, itemOrder: 1, quantity: 2,
    product: { productId: name, name, imageUrl: null, currentPrice: index ? '0.20' : '9007199254740993.99' } })),
} as PublicCombo;
beforeEach(() => { personal.mockReset(); products.mockReset(); authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null }); });
afterEach(() => authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null }));

test('checkbox totals use exact decimal prices and quantities without applying a combo discount', () => {
  expect(selectedTotal(combo.items)).toBe('18014398509481988.38');
  render(<ComboSelection combo={combo}/>);
  expect(screen.getByText(/18\.014\.398\.509\.481\.988,38 VND/)).toBeVisible();
  fireEvent.click(screen.getByRole('checkbox', { name: 'Chọn Mouse' }));
  expect(screen.getByText(/Tổng giá đang chọn: 0,4 VND/)).toBeVisible();
  expect(screen.getByText(/chưa đối chiếu lịch sử/)).toBeVisible();
  expect(screen.getByText('Bước 2')).toBeVisible();
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
    { rank: 1, combo }, { rank: 2, combo: { ...combo, comboId: 'second', name: 'Second' } },
    { rank: 3, combo: { ...combo, comboId: 'third', name: 'Third' } }] });
  render(<ProductComboSection productId="Mouse"/>);
  await screen.findByText(/Dựa trên lịch sử mua/);
  expect(screen.getAllByRole('heading', { level: 3 }).map(h => h.textContent)).toEqual(['Mouse then speaker', 'Second']);
  expect(screen.queryByText('Third')).not.toBeInTheDocument();
  const personalCard = screen.getAllByRole('article')[0];
  expect(within(personalCard).getByRole('checkbox', { name: 'Chọn Speaker' })).toBeVisible();
  expect(within(personalCard).queryByRole('checkbox', { name: 'Chọn Mouse' })).not.toBeInTheDocument();
});

test('latest card includes names, prices and images of constituent products; failure remains retryable', async () => {
  (latestPublicCombos as jest.Mock).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([combo]);
  render(<LatestCombos/>);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại combo mới nhất' }));
  const card = await screen.findByRole('article');
  expect(within(card).getByText('Speaker')).toBeVisible();
  expect(within(card).getByText('0,2 VND × 2')).toBeVisible();
  expect(within(card).getAllByRole('img').length).toBeGreaterThan(0);
});
