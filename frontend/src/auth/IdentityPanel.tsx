import React from 'react';
import { Badge, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { useSharedAuth } from './session';

export default function IdentityPanel() {
  const { user } = useSharedAuth();
  if (!user) return null;
  return <Paper p="lg"><Stack>
    <Title order={2}>Thông tin tài khoản</Title>
    <Text>{user.displayName}</Text>{user.username && <Text>@{user.username}</Text>}<Text>{user.email || 'Chưa có email'}</Text>
    <Text>Email: {user.emailVerified ? 'đã xác minh' : 'chưa xác minh'}</Text>
    <Group>{user.roles.map(role => <Badge key={role}>{role}</Badge>)}</Group>
  </Stack></Paper>;
}
