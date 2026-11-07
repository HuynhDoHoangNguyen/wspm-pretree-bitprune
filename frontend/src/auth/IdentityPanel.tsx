import React from 'react';
import { Avatar, Badge, Divider, Group, Paper, SimpleGrid, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import { useSharedAuth } from './session';

export default function IdentityPanel() {
  const { user } = useSharedAuth();
  const theme = useMantineTheme();
  const muted = theme.colors.gray[theme.colorScheme === 'dark' ? 4 : 7];
  if (!user) return null;
  const names: Record<string, string> = { ADMIN: 'Quản trị viên', CUSTOMER: 'Khách hàng' };
  return <Stack spacing="lg" py="lg" style={{ maxWidth: 960, margin: '0 auto' }} sx={{ fontFamily: '"Segoe UI", Arial, sans-serif',
    '& .mantine-Text-root, & .mantine-Title-root': { fontFamily: 'inherit' } }}>
    <div><Text size="sm" color="blue" weight={700}>HỒ SƠ CỦA BẠN</Text><Title order={2} mt={6}>Thông tin tài khoản</Title>
      <Text color={muted} mt={6}>Thông tin cá nhân và quyền truy cập của tài khoản đang đăng nhập.</Text></div>
    <Paper withBorder radius="md" p="xl" sx={theme => ({ background: theme.colorScheme === 'dark' ? theme.colors.dark[7] : theme.white })}>
      <Stack spacing="xl">
        <Group spacing="lg" noWrap><Avatar size={64} radius="md" color="blue" aria-hidden="true">{Array.from(user.displayName.trim())[0]?.toUpperCase() || 'U'}</Avatar>
          <div style={{ minWidth: 0, overflowWrap: 'anywhere' }}><Text size="xl" weight={700}>{user.displayName}</Text>
            <Text color={muted}>{user.username ? '@' + user.username : 'Chưa có tên tài khoản'}</Text></div>
        </Group><Divider/>
        <SimpleGrid cols={2} spacing="xl" breakpoints={[{ maxWidth: 'sm', cols: 1 }]}>
          <Stack spacing="md"><Title order={3} style={{ fontSize: 18 }}>Thông tin đăng nhập</Title>
            <div><Text size="sm" color={muted}>Tên tài khoản</Text><Text weight={500}>{user.username || 'Chưa có tên tài khoản'}</Text></div>
            <div style={{ overflowWrap: 'anywhere' }}><Text size="sm" color={muted}>Địa chỉ email</Text><Text weight={500}>{user.email || 'Chưa có email'}</Text></div>
          </Stack>
          <Stack spacing="md"><Title order={3} style={{ fontSize: 18 }}>Trạng thái & quyền truy cập</Title>
            <div><Text size="sm" color={muted} mb={6}>Trạng thái tài khoản</Text><Badge color="teal">Đang hoạt động</Badge></div>
            <div><Text size="sm" color={muted} mb={6}>Xác minh email</Text><Badge color={user.email && user.emailVerified ? 'teal' : 'yellow'}
              sx={user.email && user.emailVerified ? undefined : { color: theme.colorScheme === 'dark' ? '#fde68a' : '#713f12', background: theme.colorScheme === 'dark' ? '#422006' : '#fef3c7' }}>
              {user.email ? user.emailVerified ? 'Đã xác minh' : 'Chưa xác minh' : 'Chưa có email để xác minh'}</Badge></div>
            <div><Text size="sm" color={muted} mb={6}>Vai trò</Text><Group spacing="xs">{user.roles.map(role => <Badge key={role} variant="outline">{role}{names[role] ? ' · ' + names[role] : ''}</Badge>)}</Group></div>
          </Stack>
        </SimpleGrid>
      </Stack>
    </Paper>
  </Stack>;
}
