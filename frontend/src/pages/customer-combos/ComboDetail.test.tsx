import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { ComboDetailContent } from './ComboDetail';
import { CustomerCombo } from './CustomerCombos.types';

const combo: CustomerCombo = {
  comboId: 'example', name: 'Combo minh họa', price: 1000000,
  items: [{ productId: 'one', name: 'Sản phẩm một', code: 'ONE', price: 1000000, category: 'Phụ kiện' }],
};

test('detail content uses the page heading and never renders a dialog', () => {
  render(<ComboDetailContent combo={combo}/>);
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { level: 1, name: 'Combo minh họa' })).toBeInTheDocument();
});

test.each([undefined, 0, -100, 900000, 1000000, NaN, Infinity])(
  'does not invent savings with invalid or non-discounted reference price %s', referencePrice => {
    render(<ComboDetailContent combo={{ ...combo, referencePrice }}/>);
    expect(screen.queryByText(/Tiết kiệm/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Giá tham chiếu/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Giảm.*%/)).not.toBeInTheDocument();
  }
);

test('detail preserves reference price, sale price and savings without cart action', () => {
  render(<ComboDetailContent combo={{ ...combo, referencePrice: 1250000 }}/>);
  const summary = screen.getByRole('region', { name: 'Giá Combo' });
  expect(within(summary).getByText(/Giá tham chiếu:/)).toHaveTextContent('1.250.000');
  expect(within(summary).getByText(/Giá combo:/)).toHaveTextContent('1.000.000');
  expect(within(summary).getByText(/Tiết kiệm/)).toHaveTextContent('250.000');
  expect(within(summary).getByText('Giảm 20%')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /giỏ hàng/i })).not.toBeInTheDocument();
});
