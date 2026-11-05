import React from 'react';
import { Alert, Button, Container, Stack } from '@mantine/core';
import { Link, useLocation } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import AuthGate from 'auth/AuthGate';
import IdentityPanel from 'auth/IdentityPanel';

export default function ProtectedRoute({ children }: { children: JSX.Element }): JSX.Element {
  void children; // Inherited consumers need unsupported numeric-ID profiles/APIs.
  const state = useSharedAuth(); const { pathname } = useLocation();
  if (['bootstrapping', 'unavailable', 'signing-out'].includes(state.status)) return <AuthGate/>;
  if (!state.user) return <Container><Stack><Alert role="alert">Vui lòng đăng nhập để truy cập.</Alert>
    <Button component={Link} to="/signin">Đăng nhập</Button></Stack></Container>;
  if (pathname === '/user' || pathname === '/user/') return <Container><IdentityPanel/></Container>;
  return <Container><Alert role="status">Chức năng này chưa được nối với API hiện tại. Bạn đã đăng nhập.</Alert></Container>;
}
