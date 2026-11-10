import { useEffect, useState } from 'react';
import { authSession, useSharedAuth } from 'auth/session';
import { ComboApiError, personalCombos } from 'tv3/api/combos';
import { PersonalCombos } from 'tv3/models/combos';

/** Scope responses to the current identity/token and product before exposing any card. */
export default function usePersonalCombos(productId?: string, attempt = 0) {
  const { status, accessToken } = useSharedAuth();
  const token = status === 'authenticated' ? accessToken : null;
  const [state, setState] = useState<{ token: string | null; productId?: string; data?: PersonalCombos; error?: boolean }>({ token: null });
  useEffect(() => {
    if (!token) return;
    let active = true;
    const controller = new AbortController();
    const clear = authSession.onClear(() => { active = false; controller.abort(); setState({ token: null }); });
    setState({ token, productId });
    const load = productId ? personalCombos(token, controller.signal, productId) : personalCombos(token, controller.signal);
    load.then(data => { if (active) setState({ token, productId, data }); }).catch(error => {
      if (!active || controller.signal.aborted) return;
      if (error instanceof ComboApiError && error.status === 401) authSession.unauthorized();
      else setState({ token, productId, error: true });
    });
    return () => { active = false; controller.abort(); clear(); };
  }, [token, productId, attempt]);
  const current = !!token && state.token === token && state.productId === productId;
  return {
    authenticated: !!token, loading: !!token && (!current || (!state.data && !state.error)),
    error: current && !!state.error,
    items: current ? state.data?.items.filter(r => !productId || r.combo.items.some(i => i.product.productId === productId)) ?? [] : [],
  };
}
