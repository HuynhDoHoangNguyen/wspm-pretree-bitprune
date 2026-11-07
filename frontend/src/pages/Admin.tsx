import React from 'react';
import { Alert, AppShell, Button, Stack } from '@mantine/core';
import { DefaultHeader } from 'components/DefaultHeader/DefaultHeader';
import { DefaultNavbar } from 'components/DefaultNavbar/DefaultNavbar';
import { matchPath, Outlet, useLocation } from 'react-router-dom';
import AdminSignin from 'pages/admin-signin';
import AuthGate from 'auth/AuthGate';
import { authSession, useSharedAuth } from 'auth/session';
import ManagerPath from 'constants/ManagerPath';
import { useSignOut } from 'auth/useSignOut';

export default function Admin() {
  const state = useSharedAuth();
  const signOut = useSignOut();
  const { pathname } = useLocation();
  const normalized = pathname.replace(/\/+$/, '').toLowerCase();
  if (['bootstrapping', 'unavailable', 'signing-out'].includes(state.status)) return <AuthGate/>;
  if (!state.user) return <AdminSignin/>;
  if (!state.user.roles.includes('ADMIN') || state.forbidden) return <Stack p="xl">
    <Alert color="red" role="alert">Tài khoản đã đăng nhập nhưng không có quyền ADMIN hoặc máy chủ đã từ chối quyền truy cập.</Alert>
    <Button onClick={() => void authSession.checkIdentity().catch(() => undefined)}>Kiểm tra lại quyền</Button>
    <Button onClick={() => void signOut()}>Đăng xuất</Button>
  </Stack>;
  const supported = ['/admin', '/admin/datasets', '/admin/mining-runs', '/admin/patterns', '/admin/combos', ManagerPath.ACCOUNT].includes(normalized)
    || matchPath(ManagerPath.DATASETS + '/:snapshotId', normalized) !== null
    || matchPath(ManagerPath.MINING_RUNS + '/:runId', normalized) !== null;
  return <AppShell fixed header={<DefaultHeader/>} navbar={<DefaultNavbar/>}>
    {supported ? <Outlet/> : <Alert role="status">Chức năng legacy này chưa được nối với API hiện tại. Phiên đăng nhập đã được xác thực.</Alert>}
  </AppShell>;
}
