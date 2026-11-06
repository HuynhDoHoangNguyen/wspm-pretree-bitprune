import React from 'react';
import { Alert, Container } from '@mantine/core';
import { Navigate, useLocation } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import AuthGate from 'auth/AuthGate';
import IdentityPanel from 'auth/IdentityPanel';

export default function ProtectedRoute({ children }: { children: JSX.Element }): JSX.Element {
  void children; // Inherited consumers need unsupported numeric-ID profiles/APIs.
  const state = useSharedAuth(); const { pathname } = useLocation();
  if (['bootstrapping', 'unavailable', 'signing-out'].includes(state.status)) return <AuthGate/>;
  if (!state.user) return <Navigate to={pathname === '/cart' ? '/signin' : '/'} replace/>;
  if (pathname === '/user' || pathname === '/user/') return <Container><IdentityPanel/></Container>;
  return <Container><Alert role="status">Chức năng này chưa được nối với API hiện tại. Bạn đã đăng nhập.</Alert></Container>;
}
