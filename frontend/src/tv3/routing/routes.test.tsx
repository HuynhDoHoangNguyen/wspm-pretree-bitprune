import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { Link, MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { getCategories, getProduct, getProducts } from '../api/catalog';
import { listPublicCombos, readPublicCombo } from '../api/combos';
import { ProductDetailResponse } from '../models/catalog';
import { createTV3ClientRoutes, parseCatalogPage } from './routes';
import ClientHeader from 'components/ClientHeader/ClientHeader';

jest.mock('components', () => ({ ElectroLogo: () => <span>Electro</span> }));
jest.mock('auth/session', () => ({ useSharedAuth: () => ({ user: null }) }));
jest.mock('auth/useSignOut', () => ({ useSignOut: () => jest.fn() }));
jest.mock('stores/use-auth-store', () => () => ({ user: null, currentTotalCartItems: 0 }));
jest.mock('stores/use-client-site-store', () => () => ({ newNotifications: [], pushNewNotification: jest.fn() }));
jest.mock('react-query', () => ({ ...jest.requireActual('react-query'),
  useQuery: () => ({ data: { content: [{ categorySlug: 'laptop', categoryName: 'Danh mục legacy kiểm thử', categoryChildren: [] }] },
    isLoading: false, isError: false }),
}));

jest.mock('../api/catalog', () => ({ ...jest.requireActual('../api/catalog'), getCategories: jest.fn(), getProducts: jest.fn(), getProduct: jest.fn() }));
jest.mock('../api/combos', () => ({ ...jest.requireActual('../api/combos'), listPublicCombos: jest.fn(), readPublicCombo: jest.fn() }));
const comboListMock = listPublicCombos as jest.MockedFunction<typeof listPublicCombos>;
const comboReadMock = readPublicCombo as jest.MockedFunction<typeof readPublicCombo>;
const categoriesMock = getCategories as jest.MockedFunction<typeof getCategories>;
const listMock = getProducts as jest.MockedFunction<typeof getProducts>;
const detailMock = getProduct as jest.MockedFunction<typeof getProduct>;
const resizeObserver = Object.getOwnPropertyDescriptor(window, 'ResizeObserver');
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', {
  configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
afterAll(() => {
  if (resizeObserver) Object.defineProperty(window, 'ResizeObserver', resizeObserver);
  else Reflect.deleteProperty(window, 'ResizeObserver');
});
const product: ProductDetailResponse = {
  productId: '9007199254740993', productName: 'Sản phẩm thật theo contract', categoryId: 'cat/A',
  price: 1000, productStatus: null, createdAt: null, imageUrl: null, images: [],
};
function Shell() {
  const location = useLocation();
  return <><output data-testid="url">{location.pathname + location.search}</output>
    <Link to="/products?categoryId=other&page=2">Đổi danh mục</Link><Outlet/></>;
}
function mount(url: string) {
  return render(<MemoryRouter initialEntries={[url]}><Routes>
    <Route path="/" element={<Shell/>}>{createTV3ClientRoutes()}</Route>
  </Routes></MemoryRouter>);
}
beforeEach(() => {
  categoriesMock.mockReset().mockResolvedValue([{ categoryId: 'cat/A', categoryName: 'TV3 category fixture' }]);
  comboListMock.mockReset().mockReturnValue(new Promise(() => undefined));
  comboReadMock.mockReset();
  listMock.mockReset().mockResolvedValue([product]);
  detailMock.mockReset().mockResolvedValue(product);
});

test.each([null, '', '0', '-1', '1.5', 'NaN', 'Infinity', '9007199254740992', '1e2'])('page parser rejects %s safely', (value) => {
  expect(parseCatalogPage(value)).toBe(1);
});
test('page parser accepts positive safe integers and factory registers the approved catalog and Combo routes', () => {
  expect(parseCatalogPage('2')).toBe(2);
  expect(createTV3ClientRoutes().map(route => route.props.path)).toEqual(['/products', '/products/:productId', '/categories', '/combos/:comboId']);
});

test('list/detail/back navigation preserves exact product IDs and filter context', async () => {
  mount('/products?categoryId=cat%2FA');
  const link = await screen.findByRole('link', { name: 'Xem sản phẩm Sản phẩm thật theo contract' });
  fireEvent.click(link);
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(product.productName!);
  expect(detailMock).toHaveBeenCalledWith('9007199254740993', expect.any(AbortSignal));
  expect(screen.getByTestId('url')).toHaveTextContent('/products/9007199254740993?categoryId=cat%2FA');
  fireEvent.click(screen.getByRole('link', { name: 'Về danh sách sản phẩm' }));
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Danh sách sản phẩm');
  expect(screen.getByTestId('url')).toHaveTextContent('/products?categoryId=cat%2FA');
});

test('page changes update the query and category changes reset to page one', async () => {
  listMock.mockResolvedValue(Array.from({ length: 30 }, (_, index) => ({ ...product, productId: String(index), productName: `Item ${index}` })));
  mount('/products?categoryId=cat%2FA&page=2');
  await screen.findByText('Item 12');
  fireEvent.click(screen.getByRole('button', { name: '3' }));
  expect(screen.getByTestId('url')).toHaveTextContent('page=3');
  expect(screen.getAllByRole('listitem')).toHaveLength(6);
  fireEvent.click(screen.getByRole('link', { name: 'Đổi danh mục' }));
  await screen.findByText('Không có sản phẩm trong danh mục này.');
  expect(screen.getByTestId('url')).toHaveTextContent('/products?categoryId=other');
  expect(screen.getByTestId('url').textContent).not.toContain('page=');
});

test('modified clicks keep the native link behavior instead of SPA interception', async () => {
  mount('/products');
  const link = await screen.findByRole('link', { name: 'Xem sản phẩm Sản phẩm thật theo contract' });
  const event = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true, button: 0 });
  let preventedByPage: boolean | undefined;
  const stopNativeJSDOMNavigation = (click: Event) => {
    preventedByPage = click.defaultPrevented;
    click.preventDefault();
  };
  document.addEventListener('click', stopNativeJSDOMNavigation, { once: true });
  await act(async () => { link.dispatchEvent(event); });
  expect(preventedByPage).toBe(false);
  expect(screen.getByTestId('url')).toHaveTextContent('/products');
});

test('actual Electro Header has one category link, reaches categories and no longer exposes the dropdown trigger', async () => {
  render(<MemoryRouter><ClientHeader/><Routes>
    <Route path="/" element={<Shell/>}>{createTV3ClientRoutes()}
      <Route path="/category/:slug" element={<div>Legacy category retained</div>}/>
    </Route>
  </Routes></MemoryRouter>);
  const links = screen.getAllByRole('link', { name: 'Danh mục sản phẩm' });
  expect(links).toHaveLength(1);
  expect(links[0]).toHaveAttribute('href', '/categories');
  expect(screen.queryByRole('button', { name: 'Mở menu danh mục' })).not.toBeInTheDocument();
  expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  fireEvent.click(links[0]);
  expect(await screen.findByRole('heading', { name: 'Danh mục sản phẩm' })).toBeInTheDocument();
  expect(screen.getByTestId('url')).toHaveTextContent('/categories');
  expect(await screen.findByRole('link', { name: 'TV3 category fixture' })).toHaveAttribute('href', '/products?categoryId=cat%2FA');
  expect(categoriesMock).toHaveBeenCalledWith(expect.any(AbortSignal));
  expect(screen.getByPlaceholderText('Bạn tìm gì...')).toBeInTheDocument();
  expect(screen.getByLabelText('Tài khoản')).toBeInTheDocument();
}, 20000);

test('Product Detail to Combo Detail and Back uses real IDs within Client routes', async () => {
  const combo: import('../models/combos').PublicCombo = {
    comboId: 'combo/A',
    name: 'Combo API thật',
    kind: 'SAME_ORDER_BUNDLE',
    status: 'PUBLISHED',
    explanation: 'Mua cùng một lần',
    validFrom: null,
    validTo: null,
    publishedAt: null,
    evaluatedAt: '2026-11-08T01:00:00Z',
    provenance: {
      batch: {
        batchId: 'batch',
        runId: 'run',
        snapshotId: 'snapshot',
        algorithm: 'WSPM_PRETREE',
        rankingPolicy: 'SUPPORT_THEN_PATTERN_WEIGHT_V1',
        patternWeightPolicy: 'stored-policy'
      },
      primaryPatternId: '9007199254740993',
      canonicalPattern: '<(1 2)>'
    },
    metrics: {
      supportAbs: 10,
      supportRatio: 0.5,
      sequenceCount: 20,
      patternWeight: 0.8
    },
    pricing: {
      basis: 'PUBLISH_SNAPSHOT',
      currencyCode: 'VND',
      referenceOriginalPrice: '9007199254740993.99',
      referenceSalePrice: '1000.50',
      discountType: 'FIXED_AMOUNT',
      discountValue: '100.00',
      maxDiscountAmount: null,
      isCheckoutQuote: false
    },
    items: [
      {
        itemsetOrder: 1,
        itemOrder: 1,
        quantity: 2,
        anchorEligible: true,
        discountEligible: true,
        unitPriceSnapshot: '1000.50',
        product: {
          productId: '9007199254740993',
          name: 'Sản phẩm thành phần',
          sku: null,
          categoryId: 'cat/A',
          imageUrl: null,
          currentPrice: '2000.00'
        }
      }
    ]
  };
  comboListMock.mockResolvedValue({ productId: product.productId, requestedK: 4, effectiveK: 4,
    evaluatedAt: combo.evaluatedAt, provenance: combo.provenance.batch, items: [{ rank: 1, combo }] });
  comboReadMock.mockResolvedValue(combo);
  mount('/products/' + product.productId);
  fireEvent.click(await screen.findByRole('link', { name: 'Xem chi tiết Combo' }));
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(combo.name);
  expect(screen.getByTestId('url')).toHaveTextContent('/combos/combo%2FA?productId=' + product.productId);
  expect(comboReadMock).toHaveBeenCalledWith('combo/A', expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('link', { name: 'Về sản phẩm' }));
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(product.productName!);
  expect(screen.getByTestId('url')).toHaveTextContent('/products/' + product.productId);
});
