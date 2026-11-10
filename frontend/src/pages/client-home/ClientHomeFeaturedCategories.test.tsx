/* eslint-disable testing-library/no-node-access -- Decorative SVGs are aria-hidden; inspect their geometry without adding accessible names. */
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Backpack, Cpu, DeviceDesktop, DeviceGamepad, DeviceLaptop, DeviceSpeaker, Keyboard, List, Mouse } from 'tabler-icons-react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { Grid } from '@mantine/core';
import useCatalogCategories from 'hooks/catalog/useCatalogCategories';
import CategoryCard from 'components/catalog/CategoryCard';
import ClientHomeFeaturedCategories from './ClientHomeFeaturedCategories';

jest.mock('hooks/catalog/useCatalogCategories', () => ({ __esModule: true, default: jest.fn() }));
jest.mock('@mantine/core', () => {
  const actual = jest.requireActual('@mantine/core');
  const Col = actual.Grid.Col;
  return { ...actual, Grid: Object.assign((props: React.ComponentProps<typeof actual.Grid>) =>
    <actual.Grid {...props}/>, { Col: jest.fn(props => <Col {...props}/>) }) };
});
const categories = [
  ['Balo', 'backpack'], ['Bàn phím', 'keyboard'], ['Chuột', 'mouse'], ['CPU', 'cpu'],
  ['Laptop', 'device-laptop'], ['Loa', 'device-speaker'], ['Máy chơi game', 'device-gamepad'], ['PC', 'device-desktop'],
  ['Khác', 'list'], [null, 'list'], ['   ', 'list'], ['  lApToP  ', 'device-laptop'],
  ['__proto__', 'list'],
] as const;
const hook = useCatalogCategories as jest.MockedFunction<typeof useCatalogCategories>;
const icons = { backpack: Backpack, keyboard: Keyboard, mouse: Mouse, cpu: Cpu,
  'device-laptop': DeviceLaptop, 'device-speaker': DeviceSpeaker, 'device-gamepad': DeviceGamepad,
  'device-desktop': DeviceDesktop, list: List };
function geometry(icon: keyof typeof icons) {
  return renderToStaticMarkup(React.createElement(icons[icon])).replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '');
}
function Location() { return <output data-testid="location">{useLocation().search}</output>; }
function show() { return render(<MemoryRouter><ClientHomeFeaturedCategories/><Location/></MemoryRouter>); }
beforeEach(() => {
  jest.clearAllMocks();
  const actual = jest.requireActual('@mantine/core');
  (Grid.Col as unknown as jest.Mock).mockImplementation(props => <actual.Grid.Col {...props}/>);
  hook.mockReturnValue({ status: 'success', categories: categories.map(([categoryName], index) => ({
    categoryId: `cat / &${index}`, categoryName,
  })) });
});

test.each(categories)('maps %s to %s while retaining icon presentation and accessibility', (name, icon) => {
  const index = categories.findIndex(([categoryName]) => categoryName === name);
  hook.mockReturnValue({ status: 'success', categories: [{ categoryId: `cat / &${index}`, categoryName: name }] });
  show();
  const link = screen.getByRole('link', { name: name?.trim() || 'Danh mục chưa có tên' });
  const svg = link.querySelector('svg');
  expect(svg?.innerHTML).toBe(geometry(icon));
  expect(svg).toHaveAttribute('aria-hidden', 'true');
  expect(svg).toHaveAttribute('width', '50');
  expect(svg).toHaveAttribute('stroke-width', '1');
});

test('preserves API order, exact names, encoded IDs, clicks and the responsive grid contract', () => {
  show();
  const links = screen.getAllByRole('link').filter(link => link.getAttribute('href')?.startsWith('/products?'));
  expect(links.map(link => link.textContent)).toEqual(categories.map(([name]) => name?.trim() ? name : 'Danh mục chưa có tên'));
  links.forEach((link, index) => {
    expect(new URL(link.getAttribute('href') || '', 'http://test').searchParams.get('categoryId')).toBe(`cat / &${index}`);
  });
  const columns = (Grid.Col as unknown as jest.Mock).mock.calls;
  expect(columns).toHaveLength(categories.length);
  columns.forEach(([props]) => expect(props).toMatchObject({ span: 6, sm: 4, md: 3 }));
  fireEvent.click(links[2]);
  expect(new URLSearchParams(screen.getByTestId('location').textContent || '').get('categoryId')).toBe('cat / &2');
  expect(screen.getByRole('link', { name: 'Xem tất cả' })).toHaveAttribute('href', '/categories');
});

test('existing card consumers keep List when they do not pass the optional icon', () => {
  render(<MemoryRouter><CategoryCard category={{ categoryId: 'original-id', categoryName: 'Laptop' }}/></MemoryRouter>);
  const link = screen.getByRole('link', { name: 'Laptop' });
  expect(link.querySelector('svg')?.innerHTML).toBe(geometry('list'));
  expect(link).toHaveAttribute('href', '/products?categoryId=original-id');
});
