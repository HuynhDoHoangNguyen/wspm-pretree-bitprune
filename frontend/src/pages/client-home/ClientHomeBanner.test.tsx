import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import ClientHomeBanner from './ClientHomeBanner';

jest.mock('components', () => ({
  ClientCarousel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

test('uses all six owner supplied public banner assets', () => {
  render(<ClientHomeBanner/>);
  for (let index = 1; index <= 6; index++) {
    expect(screen.getByAltText(`Giới thiệu sản phẩm ${index}`)).toHaveAttribute('src',
      `/images/banners/banner0${index}.webp`);
  }
});

test('a failed banner has a fallback while the remaining slides stay available', () => {
  render(<ClientHomeBanner/>);
  fireEvent.error(screen.getByAltText('Giới thiệu sản phẩm 1'));
  expect(screen.getByRole('status')).toHaveTextContent('Banner hiện chưa khả dụng.');
  expect(screen.queryByAltText('Giới thiệu sản phẩm 1')).not.toBeInTheDocument();
  expect(screen.getAllByRole('img')).toHaveLength(5);
});
