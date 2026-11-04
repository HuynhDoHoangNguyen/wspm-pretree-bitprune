import React from 'react';
import { Alert, Button, Loader, Stack, Text } from '@mantine/core';
import { authSession, useSharedAuth } from './session';

export default function AuthGate() {
  const { status, error } = useSharedAuth();
  if (status === 'bootstrapping' || status === 'signing-out') {
    return <Stack align="center" p="xl" role="status"><Loader/><Text>{status === 'signing-out' ? 'Đang đăng xuất…' : 'Đang xác minh phiên…'}</Text></Stack>;
  }
  return <Alert color="orange" role="alert" title="Chưa xác minh được phiên">
    <Stack><Text>{error || 'Vui lòng thử khôi phục phiên hoặc đăng nhập.'}</Text>
      <Button onClick={() => void authSession.restore().catch(() => undefined)}>Khôi phục phiên</Button>
    </Stack>
  </Alert>;
}
