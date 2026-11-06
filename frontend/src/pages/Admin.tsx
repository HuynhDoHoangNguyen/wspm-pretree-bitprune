import React from 'react';
import { Alert, Button, Container, Stack } from '@mantine/core';
import { matchPath, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import AdminDashboard from './admin-auth-preview/AdminDashboard';
import { adminNavigation } from './admin-auth-preview/AdminNavigation.config';
import type { AdminSection } from './admin-auth-preview/AdminNavigation.config';
import AdminSignin from 'pages/admin-signin';
import AuthGate from 'auth/AuthGate';
import { authSession, useSharedAuth } from 'auth/session';
import ManagerPath from 'constants/ManagerPath';
import { useSignOut } from 'auth/useSignOut';

export default function Admin() {
  const state = useSharedAuth();
  const signOut = useSignOut();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const normalized = pathname.replace(/\/+$/, '').toLowerCase();
  if (['bootstrapping', 'unavailable', 'signing-out'].includes(state.status)) return <AuthGate/>;
  if (!state.user) return <AdminSignin/>;
  if (!state.user.roles.includes('ADMIN') || state.forbidden) return <Stack p="xl">
    <Alert color="red" role="alert">Tài khoản đã đăng nhập nhưng không có quyền ADMIN hoặc máy chủ đã từ chối quyền truy cập.</Alert>
    <Button onClick={() => void authSession.checkIdentity().catch(() => undefined)}>Kiểm tra lại quyền</Button>
    <Button onClick={() => void signOut()}>Đăng xuất</Button>
  </Stack>;
  if (normalized === '/admin') return <Navigate to="/admin/datasets" replace/>;
  const supported = ['/admin/datasets', '/admin/mining-runs', '/admin/mining-monitoring', '/admin/mining-comparison', '/admin/patterns', ManagerPath.ACCOUNT].includes(normalized)
    || matchPath(ManagerPath.DATASETS + '/:snapshotId', normalized) !== null;
  const destinations: Partial<Record<AdminSection, string>> = { 'data-preparation': '/admin/datasets', 'mining-runs': '/admin/mining-runs', 'mining-monitoring': '/admin/mining-monitoring', 'mining-comparison': '/admin/mining-comparison' };
  const active = normalized.startsWith('/admin/datasets') ? 'data-preparation' : adminNavigation.find(item => destinations[item.id] === normalized)?.id;
  return <div className="admin-auth-page"><Container size="xl" py="xl"><AdminDashboard mode="live" identity={state.user} onLogout={() => void signOut()}
    activeSection={active} unavailableSections={adminNavigation.filter(item => !destinations[item.id]).map(item => item.id)}
    onSelectSection={section => { const path = destinations[section]; if (path) navigate(path); }}>
    {supported ? <Outlet/> : <Alert role="status">Chức năng legacy này chưa được nối với API hiện tại. Phiên đăng nhập đã được xác thực.</Alert>}
  </AdminDashboard></Container></div>;
}
