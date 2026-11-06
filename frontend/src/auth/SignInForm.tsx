import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Container, Paper, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { authFieldErrors, AuthFormErrors, authMessage } from './api';
import { authSession, useSharedAuth } from './session';

export default function SignInForm({ admin = false }: { admin?: boolean }) {
  const state = useSharedAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState(''), [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(state.error || '');
  const [errors, setErrors] = useState<AuthFormErrors>({}), [serverErrors, setServerErrors] = useState<AuthFormErrors>({});
  const form = useRef<HTMLFormElement>(null), active = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const name = Object.keys(serverErrors)[0];
    if (!busy && name) form.current?.querySelector<HTMLInputElement>('[name="' + name + '"]')?.focus();
  }, [busy, serverErrors]);
  const destination = state.user?.roles.includes('ADMIN') ? '/admin/datasets' : '/';
  if (state.status === 'authenticated' && !admin) return <Navigate to={destination} replace/>;
  function focus(fields: AuthFormErrors) {
    const name = Object.keys(fields)[0];
    if (name) form.current?.querySelector<HTMLInputElement>('[name="' + name + '"]')?.focus();
  }
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (active.current || state.status === 'signing-out') return;
    const invalid: AuthFormErrors = {};
    if (!username.trim()) invalid.username = 'Vui lòng nhập tên tài khoản.';
    if (!password) invalid.password = 'Vui lòng nhập mật khẩu.';
    setErrors(invalid); setServerErrors({}); setError('');
    if (Object.keys(invalid).length) { focus(invalid); return; }
    active.current = true;
    setBusy(true); setError('');
    try {
      await authSession.login(username, password);
      const user = authSession.useStore.getState().user;
      if (mounted.current) {
        if (admin && !user?.roles.includes('ADMIN')) setError('Tài khoản đã đăng nhập nhưng không có quyền ADMIN.');
        else navigate(user?.roles.includes('ADMIN') ? '/admin/datasets' : '/', { replace: true });
      }
    } catch (failure) { if (mounted.current) {
      const fields = authFieldErrors(failure); setServerErrors(fields); setError(authMessage(failure));
    } }
    finally { active.current = false; if (mounted.current) setBusy(false); }
  };
  return <Container size={420} py={40}>
    <Paper withBorder shadow="sm" radius="md" p="xl"><form ref={form} aria-label={admin ? 'Đăng nhập Admin' : 'Đăng nhập'} onSubmit={submit} noValidate><Stack>
      <Title order={2}>{admin ? 'Đăng nhập Admin' : 'Đăng nhập'}</Title>
      {error && <Alert color="red" role="alert">{error}</Alert>}
      <TextInput label="Tên tài khoản" name="username" required autoComplete="username" value={username}
        error={errors.username || serverErrors.username} disabled={busy} onChange={event => {
          const value = event.currentTarget.value; setUsername(value); setServerErrors({}); setError('');
          setErrors(current => ({ ...current, username: current.username && !value.trim() ? 'Vui lòng nhập tên tài khoản.' : undefined }));
        }}/>
      <PasswordInput id="signin-password" label="Mật khẩu" name="password" required autoComplete="current-password" value={password}
        aria-invalid={!!(errors.password || serverErrors.password)} errorProps={{ id: 'signin-password-error' }}
        aria-describedby={errors.password || serverErrors.password ? 'signin-password-error' : undefined}
        error={errors.password || serverErrors.password} disabled={busy} onChange={event => {
          const value = event.currentTarget.value; setPassword(value); setServerErrors({}); setError('');
          setErrors(current => ({ ...current, password: current.password && !value ? 'Vui lòng nhập mật khẩu.' : undefined }));
        }}/>
      <Button type="submit" loading={busy} disabled={state.status === 'signing-out'}>Đăng nhập</Button>
      {!admin && <Button component={Link} to="/signup" variant="subtle">Đăng ký</Button>}
    </Stack></form></Paper>
  </Container>;
}
