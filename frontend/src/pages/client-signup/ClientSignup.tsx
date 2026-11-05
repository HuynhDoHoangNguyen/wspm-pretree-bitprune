import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Container, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { Link, Navigate } from 'react-router-dom';
import { authMessage, validRegistration } from 'auth/api';
import { authSession, useSharedAuth } from 'auth/session';

export default function ClientSignup() {
  const { user } = useSharedAuth();
  const [input, setInput] = useState({ username: '', email: '', password: '', displayName: '' });
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [done, setDone] = useState(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  if (user) return <Navigate to="/" replace/>;
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (busy) return;
    if (!validRegistration(input)) { setError('Kiểm tra username/email/tên hiển thị. Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8, không tự bỏ dấu cách.'); return; }
    setBusy(true); setError('');
    try { await authSession.register(input); if (mounted.current) { setDone(true); setInput({ username: '', email: '', password: '', displayName: '' }); } }
    catch (failure) { if (mounted.current) setError(authMessage(failure)); }
    finally { if (mounted.current) setBusy(false); }
  };
  return <Container size={460} py={40}><Stack>
    <Title order={2}>Đăng ký CUSTOMER</Title>
    {done ? <><Alert color="green" role="status">Đã tạo tài khoản. Email chưa xác minh. Vui lòng đăng nhập để tiếp tục.</Alert>
      <Button component={Link} to="/signin">Đến trang đăng nhập</Button></> :
      <form onSubmit={submit}><Stack>
        {error && <Alert color="red" role="alert">{error}</Alert>}
        <TextInput label="Tên tài khoản" autoComplete="username" required disabled={busy} value={input.username}
          onChange={event => setInput({ ...input, username: event.currentTarget.value })}/>
        <TextInput label="Email" type="email" autoComplete="email" required disabled={busy} value={input.email}
          onChange={event => setInput({ ...input, email: event.currentTarget.value })}/>
        <TextInput label="Tên hiển thị" autoComplete="name" required disabled={busy} value={input.displayName}
          onChange={event => setInput({ ...input, displayName: event.currentTarget.value })}/>
        <PasswordInput label="Mật khẩu" autoComplete="new-password" description="Ít nhất 8 ký tự, tối đa 72 byte UTF-8." required disabled={busy} value={input.password}
          onChange={event => setInput({ ...input, password: event.currentTarget.value })}/>
        <Button type="submit" loading={busy}>Đăng ký</Button>
      </Stack></form>}
  </Stack></Container>;
}
