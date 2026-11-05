import React, { ReactNode, useEffect, useState } from 'react';
import { Alert } from '@mantine/core';
import { useQueryClient } from 'react-query';
import { setWdmAuthAdapter } from 'api/admin/data-mining';
import { authSession, SessionEvent } from './session';
import { resetLegacyCart } from 'stores/use-auth-store';
import { Routes } from 'react-router-dom';

export function AuthRoutes({ children }: { children: ReactNode }) {
  return <AuthBoundary><Routes>{children}</Routes></AuthBoundary>;
}

export default function AuthBoundary({ children }: { children: ReactNode }) {
  const queries = useQueryClient();
  const [configurationError, setConfigurationError] = useState(false);
  useEffect(() => {
    // Remove inherited credentials without reading/trusting their contents.
    try {
      localStorage.removeItem('electro-auth-store');
      localStorage.removeItem('electro-admin-auth-store');
    } catch { /* Storage is optional; no authentication depends on it. */ }
    const unsubscribe = authSession.onClear(() => {
      void queries.cancelQueries(); queries.clear(); resetLegacyCart();
    });
    const base = process.env.REACT_APP_WDM_API_BASE_URL || '/api';
    try {
      if (new URL(base, window.location.href).origin !== window.location.origin) throw new Error();
    } catch {
      setConfigurationError(true);
      return () => { authSession.stop(); unsubscribe(); setWdmAuthAdapter(null); };
    }
    setWdmAuthAdapter({
      getAccessToken: () => authSession.useStore.getState().forbidden ? null : authSession.token(),
      getSessionSignal: authSession.signal,
      onUnauthorized: authSession.unauthorized, onForbidden: authSession.forbidden,
    });
    let channel: BroadcastChannel | undefined;
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        channel = new BroadcastChannel('wspm-auth-events');
        channel.onmessage = event => authSession.receive(event.data);
        authSession.setBroadcast((event: SessionEvent) => channel?.postMessage(event));
      } catch { /* Optional browser coordination; no persistent credential fallback. */ }
    }
    const focus = () => authSession.ensureFresh();
    window.addEventListener('focus', focus);
    document.addEventListener('visibilitychange', focus);
    void authSession.bootstrap().catch(() => undefined);
    return () => {
      authSession.stop(); unsubscribe(); setWdmAuthAdapter(null); channel?.close();
      window.removeEventListener('focus', focus); document.removeEventListener('visibilitychange', focus);
    };
  }, [queries]);
  return configurationError ? <Alert color="red" role="alert">Auth chỉ hỗ trợ API cùng origin; hãy kiểm tra cấu hình WDM API.</Alert> : <>{children}</>;
}
