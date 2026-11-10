import { useEffect, useState } from 'react';
import { getCategories, getProducts } from '../api/catalog';
import { CategoryResponse } from '../models/catalog';

type CategoriesState = { status: 'loading' } | { status: 'error' }
  | { status: 'success'; categories: readonly CategoryResponse[] };

export default function useCatalogCategories(): CategoriesState {
  const [state, setState] = useState<CategoriesState>({ status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    Promise.all([getCategories(controller.signal), getProducts(controller.signal)]).then(([categories, products]) => {
      if (!active) return;
      const ids = new Set(products.map((product) => product.categoryId));
      setState({ status: 'success', categories: categories.filter((category) => ids.has(category.categoryId)) });
    }).catch(() => {
      if (active && !controller.signal.aborted) setState({ status: 'error' });
    });
    return () => { active = false; controller.abort(); };
  }, []);
  return state;
}
