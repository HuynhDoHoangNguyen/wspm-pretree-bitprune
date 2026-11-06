import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Group, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { BRAND_NAME } from 'constants/Brand';
import { AdminLoginError } from './AdminLogin.types';
import type { AdminLoginAdapter, AdminLoginState } from './AdminLogin.types';
import AdminDashboard from './AdminDashboard';
import './AdminLogin.css';

interface Props {
  adapter: AdminLoginAdapter;
  initialState?: AdminLoginState;
}

export default function AdminLogin({ adapter, initialState = { status: 'guest' } }: Props) {
  useDocumentTitle(`Đăng nhập quản trị – ${BRAND_NAME} Preview`);
  const [state, setState] = useState<AdminLoginState>(initialState);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [errors, setErrors] = useState<{ username?: string; password?: string }>(initialState.status === 'validation'
    ? { username: 'Vui lòng nhập tên đăng nhập.', password: 'Vui lòng nhập mật khẩu.' } : {});
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const loading = state.status === 'loading';

  function logout() {
    request.current?.abort();
    request.current = null;
    setUsername(''); setPassword(''); setVisible(false); setErrors({});
    setState({ status: 'logged-out' });
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || loading) return;
    const trimmed = username.trim();
    const validation = {
      username: trimmed ? undefined : 'Vui lòng nhập tên đăng nhập.',
      password: password.length ? undefined : 'Vui lòng nhập mật khẩu.',
    };
    setErrors(validation);
    if (validation.username || validation.password) return;
    const controller = new AbortController();
    request.current = controller;
    const credentials = { username: trimmed, password };
    setUsername(trimmed); setPassword(''); setVisible(false); setState({ status: 'loading' });
    try {
      const identity = await adapter.signIn(credentials, controller.signal);
      if (!controller.signal.aborted) setState(identity.access === 'ADMIN'
        ? { status: 'authenticated', identity } : { status: 'denied', identity });
    } catch (error) {
      if (!controller.signal.aborted) {
        const status = error instanceof AdminLoginError
          ? (error.kind === 'forbidden' ? 'denied' : error.kind) : 'system-error';
        setState({ status });
      }
    } finally {
      if (request.current === controller) request.current = null;
    }
  }

  if (state.status === 'denied' || (state.status === 'authenticated' && state.identity.access !== 'ADMIN')) {
    return <Paper withBorder radius="lg" p="xl" className="admin-auth-card">
      <Stack><Title order={1}>Access Denied</Title>
        <Alert color="red" title="Không đủ quyền Admin">Đã xác thực trong Preview nhưng không có quyền quản trị mô phỏng.</Alert>
        <Text>Frontend guard chỉ hỗ trợ UX; backend phải xác thực và phân quyền API khi tích hợp.</Text>
        <Button onClick={logout}>Đăng xuất Preview</Button>
      </Stack>
    </Paper>;
  }
  if (state.status === 'authenticated') {
    return <AdminDashboard identity={state.identity} onLogout={logout}/>;
  }
  return <Paper withBorder radius="lg" p="xl" className="admin-auth-card">
    <Stack spacing="lg">
      <div><Text size="sm" color="blue" weight={600}>{BRAND_NAME} · ADMIN PREVIEW</Text>
        <Title order={1}>Đăng nhập quản trị</Title><Text color="dimmed" size="sm" mt="sm">Kiểm tra giao diện xác thực và phân quyền bằng dữ liệu minh họa.</Text>
      </div>
      {state.status === 'logged-out' && <Alert color="blue">Đã đăng xuất Preview. Trạng thái đăng nhập thật không thay đổi.</Alert>}
      {state.status === 'invalid-credentials' && <Alert color="red" role="alert">Tên đăng nhập hoặc mật khẩu không đúng (mô phỏng).</Alert>}
      {state.status === 'system-error' && <Alert color="red" role="alert">Không thể đăng nhập lúc này. Vui lòng thử lại.</Alert>}
      <form noValidate onSubmit={submit} aria-label="Form đăng nhập quản trị">
        <Stack spacing="md">
          <TextInput label="Tên đăng nhập" autoComplete="off" required value={username} disabled={loading}
            error={errors.username} onChange={event => setUsername(event.currentTarget.value)}/>
          <TextInput label="Mật khẩu" type={visible ? 'text' : 'password'} autoComplete="off" required value={password} disabled={loading}
            error={errors.password} onChange={event => setPassword(event.currentTarget.value)}/>
          <Group position="right"><Button type="button" variant="subtle" compact disabled={loading}
            aria-pressed={visible} onClick={() => setVisible(value => !value)}>{visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}</Button></Group>
          <Button type="submit" fullWidth color="blue" radius="md" loading={loading} disabled={loading}>Đăng nhập</Button>
          {loading && <Text role="status">Đang đăng nhập — mô phỏng…</Text>}
        </Stack>
      </form>
      <Text size="xs" color="dimmed">Không dùng thông tin đăng nhập thật trong Preview. Không có request authentication hoặc lưu mật khẩu.</Text>
    </Stack>
  </Paper>;
}
