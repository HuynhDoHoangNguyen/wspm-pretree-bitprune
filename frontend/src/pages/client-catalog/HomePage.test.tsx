import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { getCategories, getLatestProducts } from '../../api/catalog/catalog';
import { CategoryResponse, ProductResponse } from '../../models/catalog/catalog';
import HomePage, { HomePageProps } from './HomePage';

jest.mock('../../api/catalog/catalog', () => ({ getCategories: jest.fn(), getLatestProducts: jest.fn() }));
const categoriesMock = getCategories as jest.MockedFunction<typeof getCategories>;
const latestMock = getLatestProducts as jest.MockedFunction<typeof getLatestProducts>;
const props: HomePageProps = {
  getCategoryHref: (id) => `/future-list?categoryId=${encodeURIComponent(id)}`,
  productListHref: '/future-list',
  getProductHref: (id) => `/future-detail/${encodeURIComponent(id)}`,
};
// Synthetic fixtures are confined to tests; production renders only catalog API data.
const categories: readonly CategoryResponse[] = [
  { categoryId: '9007199254740993', categoryName: 'Danh mục B' },
  { categoryId: 'cat/A', categoryName: 'Danh mục A' },
  { categoryId: 'unnamed', categoryName: null },
];
const products: readonly ProductResponse[] = Array.from({ length: 7 }, (_, index) => ({
  productId: index === 0 ? '9007199254740993' : `sku/${index}`,
  productName: `Sản phẩm ${index}`, categoryId: null, productStatus: null,
  price: index === 0 ? null : index * 1000,
  imageUrl: index === 0 ? null : `/uploads/products/test-${index}.jpg`,
  // Deliberately opposite to returned order: the page must not sort these locally.
  createdAt: `2026-01-0${index + 1}T00:00:00`,
}));

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

beforeEach(() => {
  categoriesMock.mockReset().mockResolvedValue(categories);
  latestMock.mockReset().mockResolvedValue(products);
});

test('shows independent loading states and requests both catalogs with cancellation signals', () => {
  categoriesMock.mockReturnValue(new Promise(() => undefined));
  latestMock.mockReturnValue(new Promise(() => undefined));
  render(<HomePage {...props}/>);
  expect(screen.getByText('Đang tải danh mục…')).toHaveAttribute('role', 'status');
  expect(screen.getByText('Đang tải sản phẩm mới nhất…')).toHaveAttribute('role', 'status');
  expect(categoriesMock).toHaveBeenCalledTimes(1);
  expect(latestMock).toHaveBeenCalledTimes(1);
  expect(categoriesMock).toHaveBeenCalledWith(expect.any(AbortSignal));
  expect(latestMock).toHaveBeenCalledWith(expect.any(AbortSignal));
});

test('displays all API categories in server order and preserves exact IDs in links/callbacks', async () => {
  const onCategoryClick = jest.fn((_id, event) => event.preventDefault());
  render(<HomePage {...props} onCategoryClick={onCategoryClick}/>);
  const list = await screen.findByRole('list', { name: 'Danh mục' });
  const links = within(list).getAllByRole('link');
  expect(links.map((link) => link.textContent)).toEqual(['Danh mục B', 'Danh mục A', 'Danh mục chưa có tên']);
  expect(links[0]).toHaveAttribute('href', '/future-list?categoryId=9007199254740993');
  expect(links[1]).toHaveAttribute('href', '/future-list?categoryId=cat%2FA');
  fireEvent.click(links[0]);
  expect(onCategoryClick).toHaveBeenCalledWith('9007199254740993', expect.anything());
});

test('uses at most five latest ProductCards without sorting or mutating the API result', async () => {
  const onProductClick = jest.fn((_id, event) => event.preventDefault());
  const frozen = Object.freeze(products.map((product) => Object.freeze({ ...product })));
  latestMock.mockResolvedValue(frozen);
  render(<HomePage {...props} onProductClick={onProductClick}/>);
  const list = await screen.findByRole('list', { name: 'Sản phẩm mới' });
  expect(within(list).getAllByRole('listitem')).toHaveLength(5);
  const links = within(list).getAllByRole('link');
  expect(links.map((link) => link.getAttribute('aria-label'))).toEqual(
    [0, 1, 2, 3, 4].map((index) => `Xem sản phẩm Sản phẩm ${index}`),
  );
  expect(links[0]).toHaveAttribute('href', '/future-detail/9007199254740993');
  fireEvent.click(links[0]);
  expect(onProductClick).toHaveBeenCalledWith('9007199254740993', expect.anything());
  expect(screen.getByAltText('Sản phẩm 1')).toHaveAttribute('src', '/uploads/products/test-1.jpg');
  expect(screen.getByText('Chưa có giá')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: 'Chưa có ảnh cho Sản phẩm 0' })).toBeInTheDocument();
  expect(screen.queryByText('Sản phẩm 5')).not.toBeInTheDocument();
  expect(frozen).toHaveLength(7);
});

test('shows fewer than five products when that is all the API returns', async () => {
  latestMock.mockResolvedValue(products.slice(0, 2));
  render(<HomePage {...props}/>);
  const list = await screen.findByRole('list', { name: 'Sản phẩm mới' });
  expect(within(list).getAllByRole('listitem')).toHaveLength(2);
});

test('provides the caller-owned view-all href and callback even while data is loading', () => {
  categoriesMock.mockReturnValue(new Promise(() => undefined));
  latestMock.mockReturnValue(new Promise(() => undefined));
  const onViewAllProducts = jest.fn((event) => event.preventDefault());
  render(<HomePage {...props} onViewAllProducts={onViewAllProducts}/>);
  const link = screen.getByRole('link', { name: 'Xem tất cả sản phẩm' });
  expect(link).toHaveAttribute('href', '/future-list');
  fireEvent.click(link);
  expect(onViewAllProducts).toHaveBeenCalledTimes(1);
});

test('shows separate empty states without fabricated categories or products', async () => {
  categoriesMock.mockResolvedValue([]);
  latestMock.mockResolvedValue([]);
  render(<HomePage {...props}/>);
  expect(await screen.findByText('Chưa có danh mục.')).toBeInTheDocument();
  expect(await screen.findByText('Chưa có sản phẩm mới.')).toBeInTheDocument();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
});

test('category errors do not hide successful products or leak internal error details', async () => {
  categoriesMock.mockRejectedValue(new Error('private category error'));
  render(<HomePage {...props}/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được danh mục');
  expect(await screen.findByRole('list', { name: 'Sản phẩm mới' })).toBeInTheDocument();
  expect(screen.queryByText('private category error')).not.toBeInTheDocument();
});

test('product errors do not hide successful categories', async () => {
  latestMock.mockRejectedValue(new Error('private product error'));
  render(<HomePage {...props}/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được sản phẩm mới nhất');
  expect(await screen.findByRole('list', { name: 'Danh mục' })).toBeInTheDocument();
  expect(screen.queryByText('private product error')).not.toBeInTheDocument();
});

test('shows both errors when both API reads fail', async () => {
  categoriesMock.mockRejectedValue(new Error('categories failed'));
  latestMock.mockRejectedValue(new Error('latest failed'));
  render(<HomePage {...props}/>);
  expect(await screen.findAllByRole('alert')).toHaveLength(2);
});

test('hero has no invented image when no approved banner prop is supplied', async () => {
  render(<HomePage {...props}/>);
  const hero = screen.getByRole('region', { name: 'Giới thiệu' });
  expect(within(hero).getByRole('heading', { level: 1 })).toHaveTextContent('Khám phá sản phẩm');
  expect(within(hero).queryAllByText(() => true, { selector: 'img', ignore: false })).toHaveLength(0);
  await screen.findByRole('list', { name: 'Danh mục' });
});

test('hero displays the supplied banner, hides failed images and accepts a replacement prop', async () => {
  const view = render(<HomePage {...props} banner={{ src: '/test-only/banner.jpg', alt: 'Banner kiểm thử' }}/>);
  const image = screen.getByAltText('Banner kiểm thử');
  expect(image).toHaveAttribute('src', '/test-only/banner.jpg');
  fireEvent.error(image);
  expect(screen.queryByAltText('Banner kiểm thử')).not.toBeInTheDocument();
  view.rerender(<HomePage {...props} banner={{ src: '/test-only/replacement.jpg', alt: 'Banner thay thế' }}/>);
  expect(screen.getByAltText('Banner thay thế')).toHaveAttribute('src', '/test-only/replacement.jpg');
  await screen.findByRole('list', { name: 'Danh mục' });
  expect(categoriesMock).toHaveBeenCalledTimes(1);
  expect(latestMock).toHaveBeenCalledTimes(1);
});

test.each(['resolve', 'reject'])('aborts on unmount and ignores late %s from both requests', async (completion) => {
  const categoryRequest = deferred<readonly CategoryResponse[]>();
  const productRequest = deferred<readonly ProductResponse[]>();
  categoriesMock.mockReturnValue(categoryRequest.promise);
  latestMock.mockReturnValue(productRequest.promise);
  const view = render(<HomePage {...props}/>);
  const categorySignal = categoriesMock.mock.calls[0][0];
  const productSignal = latestMock.mock.calls[0][0];
  view.unmount();
  expect(categorySignal?.aborted).toBe(true);
  expect(productSignal?.aborted).toBe(true);
  await act(async () => {
    if (completion === 'resolve') {
      categoryRequest.resolve(categories);
      productRequest.resolve(products);
    } else {
      categoryRequest.reject(new Error('late category failure'));
      productRequest.reject(new Error('late product failure'));
    }
  });
});
