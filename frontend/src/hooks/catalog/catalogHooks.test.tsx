import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getCategories, getLatestProducts, getProducts } from '../../api/catalog/catalog';
import { ProductResponse } from '../../models/catalog/catalog';
import useCatalogCategories from './useCatalogCategories';
import useLatestProducts from './useLatestProducts';
import AllCategoriesPage from '../../pages/client-catalog/AllCategoriesPage';
import CategoryCard from '../../components/catalog/CategoryCard';
import ClientHomeFeaturedCategories from 'pages/client-home/ClientHomeFeaturedCategories';
import ClientHomeLatestProducts from 'pages/client-home/ClientHomeLatestProducts';

jest.mock('../../api/catalog/catalog', () => ({ getCategories: jest.fn(), getProducts: jest.fn(), getLatestProducts: jest.fn() }));
const categoriesMock = getCategories as jest.MockedFunction<typeof getCategories>;
const productsMock = getProducts as jest.MockedFunction<typeof getProducts>;
const latestMock = getLatestProducts as jest.MockedFunction<typeof getLatestProducts>;
const categories = [
  { categoryId: '9007199254740993', categoryName: 'Danh mục A' },
  { categoryId: '9007199254740992', categoryName: 'Không có sản phẩm' },
  { categoryId: 'cat / &', categoryName: 'Danh mục B' },
];
const products: readonly ProductResponse[] = Array.from({ length: 7 }, (_, i) => ({
  productId: `p/${i}`, productName: `Sản phẩm ${i}`, price: i, productStatus: 'INACTIVE',
  categoryId: i === 6 ? 'cat / &' : '9007199254740993', imageUrl: null, createdAt: null,
}));
function CategoriesProbe() { return <pre data-testid="state">{JSON.stringify(useCatalogCategories())}</pre>; }
function LatestProbe() { return <pre data-testid="state">{JSON.stringify(useLatestProducts())}</pre>; }
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
beforeEach(() => {
  categoriesMock.mockReset().mockResolvedValue(categories);
  productsMock.mockReset().mockResolvedValue(products);
  latestMock.mockReset().mockResolvedValue(products);
});

test('categories join the complete catalog by exact string ID, including inactive products', async () => {
  render(<MemoryRouter><AllCategoriesPage/></MemoryRouter>);
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải');
  const list = await screen.findByRole('list');
  expect(within(list).getAllByRole('listitem')).toHaveLength(2);
  expect(screen.queryByText('Không có sản phẩm')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Danh mục A' })).toHaveAttribute('href', '/products?categoryId=9007199254740993');
  const encoded = screen.getByRole('link', { name: 'Danh mục B' }).getAttribute('href')!;
  expect(new URL(encoded, 'http://test').searchParams.get('categoryId')).toBe('cat / &');
  expect(productsMock).toHaveBeenCalledWith(expect.any(AbortSignal));
});

test('category card uses caller data, preserves string ID and handles nullable names', () => {
  render(<MemoryRouter><CategoryCard category={{ categoryId: '0', categoryName: null }}/></MemoryRouter>);
  expect(screen.getByRole('link', { name: 'Danh mục chưa có tên' })).toHaveAttribute('href', '/products?categoryId=0');
});

test('no products means no eligible categories, rather than hardcoded cards', async () => {
  productsMock.mockResolvedValue([]);
  render(<MemoryRouter><AllCategoriesPage/></MemoryRouter>);
  expect(await screen.findByText('Chưa có danh mục có sản phẩm.')).toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
});

test.each(['categories', 'products'])('failure of %s is an error, never an empty category result', async (failed) => {
  if (failed === 'categories') categoriesMock.mockRejectedValue(new Error('private'));
  else productsMock.mockRejectedValue(new Error('private'));
  render(<MemoryRouter><AllCategoriesPage/></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được danh mục');
  expect(screen.queryByText('Chưa có danh mục có sản phẩm.')).not.toBeInTheDocument();
});

test('category hook cancels both requests and ignores completion after unmount', async () => {
  const pending = deferred<readonly ProductResponse[]>();
  productsMock.mockReturnValue(pending.promise);
  const view = render(<CategoriesProbe/>);
  const categorySignal = categoriesMock.mock.calls[0][0];
  const productSignal = productsMock.mock.calls[0][0];
  view.unmount();
  expect(categorySignal?.aborted).toBe(true);
  expect(productSignal?.aborted).toBe(true);
  await act(async () => { pending.resolve(products); });
});

test('latest hook preserves backend order and selects exactly the first four without mutating it', async () => {
  const frozen = Object.freeze([...products].reverse());
  latestMock.mockResolvedValue(frozen);
  render(<LatestProbe/>);
  await screen.findByText(/"success"/);
  expect(JSON.parse(screen.getByTestId('state').textContent!).products.map((p: ProductResponse) => p.productId))
    .toEqual(frozen.slice(0, 4).map(p => p.productId));
  expect(frozen).toHaveLength(7);
});

test('latest hook surfaces failures and cancels pending requests', async () => {
  latestMock.mockRejectedValueOnce(new Error('private'));
  const view = render(<LatestProbe/>);
  await screen.findByText(/"error"/);
  view.unmount();
  const pending = deferred<readonly ProductResponse[]>();
  latestMock.mockReturnValueOnce(pending.promise);
  const utils = render(<LatestProbe/>);
  const signal = latestMock.mock.calls[1][0];
  utils.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => { pending.reject(new Error('cancelled')); });
});

test('Electro homepage sections retain titles, use live clients and link view-all to new pages', async () => {
  render(<MemoryRouter><ClientHomeFeaturedCategories/><ClientHomeLatestProducts/></MemoryRouter>);
  await screen.findByRole('link', { name: 'Danh mục A' });
  await screen.findByText('Sản phẩm 3');
  expect(screen.getByRole('heading', { name: 'Danh mục nổi bật' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Sản phẩm mới nhất' })).toBeInTheDocument();
  const all = screen.getAllByRole('link', { name: 'Xem tất cả' });
  expect(all.map(a => a.getAttribute('href'))).toEqual(['/categories', '/products']);
  expect(screen.getAllByRole('article')).toHaveLength(4);
  expect(screen.queryByText('Sản phẩm 4')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Xem sản phẩm Sản phẩm 0' })).toHaveAttribute('href', '/products/p%2F0');
  fireEvent.click(screen.getByRole('link', { name: 'Danh mục B' }));
});

test('Electro latest section renders true empty and failure states', async () => {
  latestMock.mockResolvedValueOnce([]);
  const view = render(<MemoryRouter><ClientHomeLatestProducts/></MemoryRouter>);
  expect(await screen.findByText('Không có sản phẩm')).toBeInTheDocument();
  view.unmount();
  latestMock.mockRejectedValueOnce(new Error('private'));
  render(<MemoryRouter><ClientHomeLatestProducts/></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Đã có lỗi xảy ra');
});
