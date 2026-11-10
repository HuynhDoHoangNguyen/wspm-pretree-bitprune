import { useEffect, useState } from 'react';
import { getLatestProducts } from '../api/catalog';
import { ProductResponse } from '../models/catalog';

type LatestState = { status: 'loading' } | { status: 'error' }
  | { status: 'success'; products: readonly ProductResponse[] };

export default function useLatestProducts(): LatestState {
  const [state, setState] = useState<LatestState>({ status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    getLatestProducts(controller.signal).then((products) => {
      if (active) setState({ status: 'success', products: products.slice(0, 4) });
    }).catch(() => {
      if (active && !controller.signal.aborted) setState({ status: 'error' });
    });
    return () => { active = false; controller.abort(); };
  }, []);
  return state;
}
