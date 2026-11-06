import React, { useMemo, useState } from 'react';
import { Alert, Container, MantineProvider, NativeSelect } from '@mantine/core';
import AdminLogin from './AdminLogin';
import { AdminLoginError } from './AdminLogin.types';
import type { AdminLoginAdapter, AdminLoginState, PreviewIdentity } from './AdminLogin.types';

type Scenario = 'guest' | 'validation' | 'loading' | 'admin' | 'invalid-credentials' | 'user' | 'system-error' | 'logout';
const admin: PreviewIdentity = { username: 'preview-admin', displayName: 'Quản trị viên minh họa', access: 'ADMIN' };
const user: PreviewIdentity = { username: 'preview-user', displayName: 'Người dùng minh họa', access: 'USER' };

function initialState(scenario: Scenario): AdminLoginState {
  if (scenario === 'admin') return { status: 'authenticated', identity: admin };
  if (scenario === 'user') return { status: 'denied', identity: user };
  if (scenario === 'logout') return { status: 'logged-out' };
  return { status: scenario };
}

function adapterFor(scenario: Scenario): AdminLoginAdapter {
  return { async signIn() {
    // Scenario determines the simulated outcome; no real account/password is checked or stored.
    if (scenario === 'invalid-credentials' || scenario === 'system-error') throw new AdminLoginError(scenario);
    return scenario === 'user' ? user : admin;
  } };
}

export default function AdminLoginPreview() {
  const [scenario, setScenario] = useState<Scenario>('guest');
  const adapter = useMemo(() => adapterFor(scenario), [scenario]);
  return <MantineProvider theme={{ colorScheme: 'light', primaryColor: 'blue' }}>
    <div className="admin-auth-page"><Container size="xl" py="xl">
      <Alert color="blue" mb="lg">Dữ liệu đăng nhập minh họa — không phải authentication thật. Frontend guard không thay thế bảo mật API backend.</Alert>
      <NativeSelect label="Tình huống preview" value={scenario} mb="xl" data={[
        { value: 'guest', label: 'Chưa đăng nhập' }, { value: 'validation', label: 'Validation error' },
        { value: 'loading', label: 'Đang đăng nhập' }, { value: 'admin', label: 'Admin đăng nhập thành công' },
        { value: 'invalid-credentials', label: 'Sai thông tin đăng nhập' }, { value: 'user', label: 'User không có quyền Admin' },
        { value: 'system-error', label: 'Lỗi hệ thống' }, { value: 'logout', label: 'Đăng xuất' },
      ]} onChange={event => setScenario(event.currentTarget.value as Scenario)}/>
      <AdminLogin key={scenario} adapter={adapter} initialState={initialState(scenario)}/>
    </Container></div>
  </MantineProvider>;
}
