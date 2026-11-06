import React from 'react';
import { ColorSchemeProvider } from '@mantine/core';
import { MemoryRouter } from 'react-router-dom';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import CustomerCombos from './CustomerCombos';
import { CustomerComboDataSource } from './CustomerCombos.api';
import { CustomerCombo, CustomerProduct } from './CustomerCombos.types';
import ComboRecommendationCard from './ComboRecommendationCard';
import ProductSelectionCard from './ProductSelectionCard';

beforeAll(() => Object.defineProperty(window, 'ResizeObserver', { configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
function StoreContext({ children }: { children: React.ReactNode }) {
  return <MemoryRouter><ColorSchemeProvider colorScheme="light" toggleColorScheme={() => undefined}>{children}</ColorSchemeProvider></MemoryRouter>;
}

const products: CustomerProduct[] = [
  { productId: 'p-a', code: 'A', name: 'Laptop A', category: 'Laptop', price: 10000000 },
  { productId: 'p-b', code: 'B', name: 'Chuột B', category: 'Phụ kiện', price: 500000 },
];
const combo = (id: string): CustomerCombo => ({ comboId: id, name: `Bộ ${id}`, items: products, price: 10000000 });

test('renders whole combos in source order, caps K and keeps detail as an optional TV3-02 seam', async () => {
  const detail = jest.fn();
  const listCombos = jest.fn().mockResolvedValue([combo('z'), combo('a'), combo('c')]);
  render(<CustomerCombos products={products} source={{ listCombos }} limit={2} onViewDetail={detail}/>, { wrapper: StoreContext });
  expect(await screen.findAllByRole('article')).toHaveLength(2);
  const cards = screen.getAllByRole('article');
  expect(cards.map(card => card.getAttribute('aria-label'))).toEqual(['Bộ z', 'Bộ a']);
  expect(within(cards[0]).getByText('Laptop A')).toBeInTheDocument();
  expect(within(cards[0]).getByText('Chuột B')).toBeInTheDocument();
  expect(listCombos).toHaveBeenCalledWith('p-a', 2, expect.any(AbortSignal));
  fireEvent.click(within(cards[0]).getByRole('button', { name: 'Xem chi tiết Bộ z' }));
  expect(detail).toHaveBeenCalledWith('z');
});

test('changing string productId clears previous cards and ignores late responses', async () => {
  let finishA: (items: CustomerCombo[]) => void = () => undefined;
  const source: CustomerComboDataSource = { listCombos: jest.fn((id: string) =>
    id === 'p-a' ? new Promise<CustomerCombo[]>(resolve => { finishA = resolve; }) : Promise.resolve([combo('new')])) };
  render(<CustomerCombos products={products} source={source} limit={4}/>, { wrapper: StoreContext });
  await waitFor(() => expect(source.listCombos).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText('Sản phẩm'), { target: { value: 'p-b' } });
  expect(screen.getByRole('status', { name: 'Đang tải combo' })).toBeInTheDocument();
  await screen.findByRole('article', { name: 'Bộ new' });
  finishA([combo('old')]);
  await waitFor(() => expect(screen.queryByRole('article', { name: 'Bộ old' })).not.toBeInTheDocument());
  expect(screen.getByText('Mã sản phẩm: B')).toBeInTheDocument();
});

test('shows an empty state', async () => {
  render(<CustomerCombos products={products} source={{ listCombos: async () => [] }} limit={4}/>, { wrapper: StoreContext });
  expect(await screen.findByText('Chưa có combo gợi ý')).toBeInTheDocument();
});

test('shows an error and retries the same product', async () => {
  const listCombos = jest.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([combo('retry')]);
  render(<CustomerCombos products={products} source={{ listCombos }} limit={4}/>, { wrapper: StoreContext });
  await screen.findByRole('alert');
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByRole('article', { name: 'Bộ retry' })).toBeInTheDocument();
  expect(listCombos).toHaveBeenCalledTimes(2);
});

test('does not invent discounts or detail routes', () => {
  render(<ComboRecommendationCard combo={{ ...combo('plain'), referencePrice: 9000000 }}/>);
  expect(screen.queryByText(/Tiết kiệm/)).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Xem chi tiết Bộ plain' })).toBeDisabled();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
});

test('displays a valid reference price and savings', () => {
  render(<ComboRecommendationCard combo={{ ...combo('sale'), referencePrice: 10500000 }}/>);
  expect(screen.getByText(/Giá tham chiếu/)).toBeInTheDocument();
  expect(screen.getByText(/Tiết kiệm/)).toHaveTextContent('500.000');
});

test('product selection updates Top-K and detail navigation without cart controls', async () => {
  const detail = jest.fn();
  const listCombos = jest.fn(async (id: string) => [combo(`${id}-first`), combo(`${id}-second`)]);
  render(<CustomerCombos products={products} source={{ listCombos }} limit={1} onViewDetail={detail}/>);
  await screen.findByRole('article', { name: 'Bộ p-a-first' });
  expect(screen.queryByRole('button', { name: /giỏ hàng/i })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Sản phẩm'), { target: { value: 'p-b' } });
  await screen.findByRole('article', { name: 'Bộ p-b-first' });
  expect(screen.queryByRole('article', { name: 'Bộ p-a-first' })).not.toBeInTheDocument();
  expect(screen.getAllByRole('article')).toHaveLength(1);
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết Bộ p-b-first' }));
  expect(detail).toHaveBeenCalledWith('p-b-first');
});

test('product card has no cart callback or purchase action', () => {
  render(<ProductSelectionCard product={products[0]}/>);
  expect(screen.getByText('Mã sản phẩm: A')).toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});
