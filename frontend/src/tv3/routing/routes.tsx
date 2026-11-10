import React, { useEffect, useRef } from 'react';
import { Route, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import AllCategoriesPage from '../pages/AllCategoriesPage';
import ProductListPage from '../pages/ProductListPage';
import ProductDetailPage from '../pages/ProductDetailPage';
import ComboDetailPage from '../pages/ComboDetailPage';

export function parseCatalogPage(value: string | null): number {
  if (value === null || !/^[1-9]\d*$/.test(value)) return 1;
  const page = Number(value);
  return Number.isSafeInteger(page) ? page : 1;
}

function useAnchorNavigation() {
  const navigate = useNavigate();
  return (href: string, event: React.MouseEvent<HTMLAnchorElement>) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigate(href);
  };
}

function ProductListRoute() {
  const [params, setParams] = useSearchParams();
  const categoryId = params.get('categoryId');
  const previousCategory = useRef(categoryId);
  const categoryChanged = previousCategory.current !== categoryId;
  const onAnchor = useAnchorNavigation();
  const productHref = (id: string) => '/products/' + encodeURIComponent(id) + (params.toString() ? '?' + params : '');
  useEffect(() => {
    if (previousCategory.current === categoryId) return;
    previousCategory.current = categoryId;
    const next = new URLSearchParams(params);
    next.delete('page');
    setParams(next, { replace: true });
  }, [categoryId, params, setParams]);
  return <ProductListPage categoryId={categoryId}
    page={categoryChanged ? 1 : parseCatalogPage(params.get('page'))}
    onPageChange={(page) => {
      const next = new URLSearchParams(params);
      if (page === 1) next.delete('page'); else next.set('page', String(page));
      setParams(next);
    }}
    getProductHref={productHref} onProductClick={(id, event) => onAnchor(productHref(id), event)}/>;
}

function ProductDetailRoute() {
  const { productId } = useParams();
  const location = useLocation();
  const onAnchor = useAnchorNavigation();
  const listHref = '/products' + location.search;
  return <ProductDetailPage productId={productId ?? ''} productListHref={listHref}
    onBackToList={(event) => onAnchor(listHref, event)}/>;
}

/** Actual Route elements, registered under the existing Electro Client layout. */
function ComboDetailRoute() {
  const { comboId } = useParams();
  const [params] = useSearchParams();
  return <ComboDetailPage comboId={comboId ?? ''} productId={params.get('productId')}/>;
}

export function createTV3ClientRoutes() {
  return [
    <Route key="tv3-products" path="/products" element={<ProductListRoute/>}/>,
    <Route key="tv3-product" path="/products/:productId" element={<ProductDetailRoute/>}/>,
    <Route key="tv3-categories" path="/categories" element={<AllCategoriesPage/>}/>,
    <Route key="tv3-combo" path="/combos/:comboId" element={<ComboDetailRoute/>}/>,
  ];
}
