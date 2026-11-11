import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import ProductCard from './ProductCard';
import { ProductResponse } from '../../models/catalog/catalog';

const product: ProductResponse = {
  productId: '9007199254740993', productName: 'Laptop', categoryId: null,
  productStatus: 'ACTIVE', price: 1234.5, imageUrl: '/uploads/products/sample.jpg', createdAt: null,
};
const getProductHref = (id: string) => `/approved-products/${encodeURIComponent(id)}`;

test('renders name, price, image and a caller-owned link without a router provider', () => {
  render(<ProductCard product={product} getProductHref={getProductHref}/>);
  expect(screen.getByRole('link', { name: 'Xem sản phẩm Laptop' })).toHaveAttribute('href', '/approved-products/9007199254740993');
  expect(screen.getByRole('img', { name: 'Laptop' })).toHaveAttribute('src', product.imageUrl);
  expect(screen.getByText('1.234,5 ₫')).toBeVisible();
});

test('delegates selection with the exact ID and lets the caller control navigation', () => {
  const selected = jest.fn();
  render(<ProductCard product={product} getProductHref={getProductHref}
    onProductClick={(id, event) => { event.preventDefault(); selected(id); }}/>);
  fireEvent.click(screen.getByRole('link'));
  expect(selected).toHaveBeenCalledWith('9007199254740993');
});

test('shows explicit missing fields and preserves a real zero price', () => {
  const { rerender } = render(<ProductCard product={{ ...product, productName: null, price: null, imageUrl: null }} getProductHref={getProductHref}/>);
  expect(screen.getByText('Sản phẩm chưa có tên')).toBeVisible();
  expect(screen.getByText('Chưa có giá')).toBeVisible();
  expect(screen.getByRole('img', { name: 'Chưa có ảnh cho Sản phẩm chưa có tên' })).toBeVisible();
  expect(within(document.documentElement).queryAllByText(() => true, { selector: 'img', ignore: false })).toHaveLength(0);
  rerender(<ProductCard product={{ ...product, price: 0 }} getProductHref={getProductHref}/>);
  expect(screen.getByText('0 ₫')).toBeVisible();
});

test('falls back on an image error and attempts a changed image URL', () => {
  const { rerender } = render(<ProductCard product={product} getProductHref={getProductHref}/>);
  fireEvent.error(screen.getByRole('img', { name: 'Laptop' }));
  expect(screen.getByRole('img', { name: 'Chưa có ảnh cho Laptop' })).toBeVisible();
  expect(within(document.documentElement).queryAllByText(() => true, { selector: 'img', ignore: false })).toHaveLength(0);
  rerender(<ProductCard product={{ ...product, imageUrl: '/uploads/products/other.jpg' }} getProductHref={getProductHref}/>);
  expect(screen.getByRole('img', { name: 'Laptop' })).toHaveAttribute('src', '/uploads/products/other.jpg');
});
