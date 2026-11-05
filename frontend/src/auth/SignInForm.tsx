import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Container, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { authMessage } from './api';
import { authSession, useSharedAuth } from './session';

export default function SignInForm({ admin = false }: { admin?: boolean }) {
  const state = useSharedAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState(''), [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  if (state.status === 'authenticated' && !admin) return <Navigate to="/user" replace/>;
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError('');
    try {
      await authSession.login(username, password);
      const user = authSession.useStore.getState().user;
      if (mounted.current) {
        if (admin && !user?.roles.includes('ADMIN')) setError('Tài khoản đã đăng nhập nhưng không có quyền ADMIN.');
        else navigate(admin ? '/admin/datasets' : '/user', { replace: true });
      }
    } catch (failure) { if (mounted.current) setError(authMessage(failure)); }
    finally { if (mounted.current) setBusy(false); }
  };
  return <Container size={420} py={40}>
    <form onSubmit={submit}><Stack>
      <Title order={2}>{admin ? 'Đăng nhập Admin' : 'Đăng nhập'}</Title>
      {(error || state.error) && <Alert color="red" role="alert">{error || state.error}</Alert>}
      <TextInput label="Tên tài khoản" required autoComplete="username" value={username}
        disabled={busy} onChange={event => setUsername(event.currentTarget.value)}/>
      <PasswordInput label="Mật khẩu" required autoComplete="current-password" value={password}
        disabled={busy} onChange={event => setPassword(event.currentTarget.value)}/>
      <Button type="submit" loading={busy} disabled={state.status === 'signing-out'}>Đăng nhập</Button>
      <Button component={Link} to="/signup" variant="subtle">Đăng ký CUSTOMER</Button>
      <Button variant="subtle" disabled={busy} onClick={() => void authSession.restore().catch(() => undefined)}>Khôi phục phiên</Button>
    </Stack></form>
  </Container>;
}
