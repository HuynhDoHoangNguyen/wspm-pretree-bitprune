/* eslint-disable testing-library/no-node-access -- Icons are decorative and aria-hidden; compare SVG geometry without changing accessible names. */
import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { Backpack, Cpu, DeviceDesktop, DeviceGamepad, DeviceLaptop, DeviceSpeaker, Keyboard, List, Mouse } from 'tabler-icons-react';
import { categoryIcons } from '../client-home/ClientHomeFeaturedCategories';
import CategoryCard from '../../components/catalog/CategoryCard';
import useCatalogCategories from '../../hooks/catalog/useCatalogCategories';
import AllCategoriesPage from './AllCategoriesPage';

jest.mock('../../hooks/catalog/useCatalogCategories', () => ({ __esModule: true, default: jest.fn() }));
const hook = useCatalogCategories as jest.MockedFunction<typeof useCatalogCategories>;
// Expectations are test data only; production uses the exported Homepage map.
const cases = [
  ['Balo', Backpack], ['Bàn phím', Keyboard], ['Chuột', Mouse], ['CPU', Cpu],
  ['Laptop', DeviceLaptop], ['Loa', DeviceSpeaker], ['Máy chơi game', DeviceGamepad], ['PC', DeviceDesktop],
  ['Khác', List], [null, List], ['   ', List], ['  lApToP  ', DeviceLaptop], ['__proto__', List],
] as const;
function geometry(IconComponent: typeof List) {
  return renderToStaticMarkup(<IconComponent/>).replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '');
}
function Location() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}
function show() { return render(<MemoryRouter initialEntries={['/categories']}><AllCategoriesPage/><Location/></MemoryRouter>); }
beforeEach(() => {
  hook.mockReset().mockReturnValue({ status: 'success', categories: cases.map(([categoryName], index) => ({
    categoryId: `cat / &${index}`, categoryName,
  })) });
});
afterEach(() => { jest.restoreAllMocks(); });

test.each(cases)('uses the shared Homepage mapping and correct icon for %s', (name, ExpectedIcon) => {
  hook.mockReturnValue({ status: 'success', categories: [{ categoryId: '9007199254740993', categoryName: name }] });
  const lookup = jest.spyOn(categoryIcons, 'get');
  show();
  expect(lookup).toHaveBeenCalledWith((name ?? '').trim().toLowerCase());
  const link = screen.getByRole('link', { name: name?.trim() || 'Danh mục chưa có tên' });
  expect(link.querySelector('svg')?.innerHTML).toBe(geometry(ExpectedIcon));
  expect(link.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  expect(link).toHaveAttribute('href', '/products?categoryId=9007199254740993');
});

test('keeps original names, order, string IDs and filtered product navigation', () => {
  show();
  const list = screen.getByRole('list', { name: 'Danh mục có sản phẩm' });
  expect(within(list).getAllByRole('listitem')).toHaveLength(cases.length);
  const links = within(list).getAllByRole('link');
  expect(links.map(link => link.textContent)).toEqual(cases.map(([name]) => name?.trim() ? name : 'Danh mục chưa có tên'));
  links.forEach((link, index) => {
    expect(new URL(link.getAttribute('href') || '', 'http://test').searchParams.get('categoryId')).toBe(`cat / &${index}`);
  });
  fireEvent.click(links[2]);
  const destination = new URL(screen.getByTestId('location').textContent || '', 'http://test');
  expect(destination.pathname).toBe('/products');
  expect(destination.searchParams.get('categoryId')).toBe('cat / &2');
});

test.each([
  [{ status: 'loading' } as const, 'status', 'Đang tải danh mục…'],
  [{ status: 'error' } as const, 'alert', 'Không tải được danh mục. Vui lòng thử lại sau.'],
  [{ status: 'success', categories: [] } as const, 'status', 'Chưa có danh mục có sản phẩm.'],
] as const)('preserves the %j state without category cards', (state, role, message) => {
  hook.mockReturnValue(state);
  show();
  expect(screen.getByRole('heading', { name: 'Danh mục sản phẩm' })).toBeVisible();
  expect(screen.getByRole(role)).toHaveTextContent(message);
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
});

test('existing CategoryCard consumers still default to List without the icon prop', () => {
  render(<MemoryRouter><CategoryCard category={{ categoryId: 'unchanged', categoryName: 'Laptop' }}/></MemoryRouter>);
  const link = screen.getByRole('link', { name: 'Laptop' });
  expect(link.querySelector('svg')?.innerHTML).toBe(geometry(List));
  expect(link).toHaveAttribute('href', '/products?categoryId=unchanged');
});
