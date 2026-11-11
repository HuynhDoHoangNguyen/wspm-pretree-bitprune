import React from 'react';
import { Alert, Button, Loader, Stack, Text } from '@mantine/core';
import { Link } from 'react-router-dom';
import { useSharedAuth } from './session';

export default function AuthGate() {
  const { status, error } = useSharedAuth();
  if (status === 'bootstrapping' || status === 'signing-out') {
    return <Stack align="center" p="xl" role="status"><Loader/><Text>{status === 'signing-out' ? 'Đang đăng xuất…' : 'Đang xác minh phiên…'}</Text></Stack>;
  }
  return <Alert color="orange" role="alert" title="Chưa xác minh được phiên">
    <Stack><Text>{error || 'Vui lòng đăng nhập lại để tiếp tục.'}</Text>
      <Button component={Link} to="/signin">Đăng nhập lại</Button>
      <Button component={Link} to="/" variant="subtle">Về cửa hàng</Button>
    </Stack>
  </Alert>;
}
