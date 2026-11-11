import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Container, Paper, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { Link, Navigate } from 'react-router-dom';
import { authFieldErrors, AuthFormErrors, authMessage, RegistrationInput, registrationErrors } from 'auth/api';
import { authSession, useSharedAuth } from 'auth/session';

export default function ClientSignup() {
  const { user } = useSharedAuth();
  const [input, setInput] = useState({ username: '', email: '', password: '', displayName: '' });
  const [confirmation, setConfirmation] = useState('');
  const [errors, setErrors] = useState<AuthFormErrors>({}), [serverErrors, setServerErrors] = useState<AuthFormErrors>({});
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [done, setDone] = useState(false);
  const mounted = useRef(true);
  const form = useRef<HTMLFormElement>(null), active = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const name = Object.keys(serverErrors)[0];
    if (!busy && name) form.current?.querySelector<HTMLInputElement>('[name="' + name + '"]')?.focus();
  }, [busy, serverErrors]);
  if (user) return <Navigate to={user.roles.includes('ADMIN') ? '/admin' : '/'} replace/>;
  function validate(value: RegistrationInput, repeated: string): AuthFormErrors {
    const invalid = registrationErrors(value);
    if (!repeated) invalid.passwordConfirmation = 'Vui lòng xác nhận mật khẩu.';
    else if (repeated !== value.password) invalid.passwordConfirmation = 'Mật khẩu xác nhận không khớp.';
    return invalid;
  }
  function focus(fields: AuthFormErrors) {
    const name = Object.keys(fields)[0];
    if (name) form.current?.querySelector<HTMLInputElement>('[name="' + name + '"]')?.focus();
  }
  function change(field: keyof RegistrationInput | 'passwordConfirmation', value: string) {
    const updated = field === 'passwordConfirmation' ? input : { ...input, [field]: value };
    const repeated = field === 'passwordConfirmation' ? value : confirmation;
    setInput(updated); setConfirmation(repeated); setServerErrors({}); setError('');
    const invalid = validate(updated, repeated);
    setErrors(current => {
      const next = { ...current };
      for (const key of Object.keys(current) as (keyof AuthFormErrors)[]) next[key] = invalid[key];
      if (repeated) next.passwordConfirmation = invalid.passwordConfirmation;
      return next;
    });
  }
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (active.current) return;
    const invalid = validate(input, confirmation); setErrors(invalid); setServerErrors({}); setError('');
    if (Object.keys(invalid).length) { focus(invalid); return; }
    active.current = true;
    setBusy(true); setError('');
    try { await authSession.register(input); if (mounted.current) { setDone(true); setInput({ username: '', email: '', password: '', displayName: '' }); setConfirmation(''); } }
    catch (failure) { if (mounted.current) { const fields = authFieldErrors(failure); setServerErrors(fields); setError(authMessage(failure)); } }
    finally { active.current = false; if (mounted.current) setBusy(false); }
  };
  return <Container size={460} py={40}><Paper withBorder shadow="sm" radius="md" p="xl"><Stack>
    <Title order={2}>Đăng ký</Title>
    {done ? <><Alert color="green" role="status">Đã tạo tài khoản. Email chưa xác minh. Vui lòng đăng nhập để tiếp tục.</Alert>
      <Button component={Link} to="/signin">Đến trang đăng nhập</Button></> :
      <form ref={form} aria-label="Đăng ký" onSubmit={submit} noValidate><Stack>
        {error && <Alert color="red" role="alert">{error}</Alert>}
        <TextInput label="Tên tài khoản" name="username" autoComplete="username" required disabled={busy} value={input.username}
          error={errors.username || serverErrors.username} onChange={event => change('username', event.currentTarget.value)}/>
        <TextInput label="Email" name="email" type="email" autoComplete="email" required disabled={busy} value={input.email}
          error={errors.email || serverErrors.email} onChange={event => change('email', event.currentTarget.value)}/>
        <TextInput label="Tên hiển thị" name="displayName" autoComplete="name" required disabled={busy} value={input.displayName}
          error={errors.displayName || serverErrors.displayName} onChange={event => change('displayName', event.currentTarget.value)}/>
        <PasswordInput id="signup-password" label="Mật khẩu" name="password" autoComplete="new-password" description="Ít nhất 8 ký tự, tối đa 72 byte UTF-8." required disabled={busy} value={input.password}
          aria-invalid={!!(errors.password || serverErrors.password)} errorProps={{ id: 'signup-password-error' }}
          descriptionProps={{ id: 'signup-password-help' }} aria-describedby={'signup-password-help' + (errors.password || serverErrors.password ? ' signup-password-error' : '')}
          error={errors.password || serverErrors.password} onChange={event => change('password', event.currentTarget.value)}/>
        <PasswordInput id="signup-password-confirmation" label="Xác nhận mật khẩu" name="passwordConfirmation" autoComplete="new-password" required disabled={busy} value={confirmation}
          aria-invalid={!!errors.passwordConfirmation} errorProps={{ id: 'signup-confirmation-error' }}
          aria-describedby={errors.passwordConfirmation ? 'signup-confirmation-error' : undefined}
          error={errors.passwordConfirmation} onChange={event => change('passwordConfirmation', event.currentTarget.value)}/>
        <Button type="submit" loading={busy}>Đăng ký</Button>
      </Stack></form>}
  </Stack></Paper></Container>;
}
