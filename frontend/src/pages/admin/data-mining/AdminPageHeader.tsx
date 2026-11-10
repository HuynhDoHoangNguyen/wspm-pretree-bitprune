import React from 'react';
import { Group, Paper, Stack, Text, Title } from '@mantine/core';

export default function AdminPageHeader({ title, description, action, children, order = 2 }: {
  title: string; description: string; action?: React.ReactNode; children?: React.ReactNode; order?: 1 | 2;
}) {
  return <Paper withBorder radius="md" p="md">
    <Group position="apart" align="center" spacing="sm">
      <Stack spacing={4} style={{ flex: '1 1 260px', minWidth: 0 }}>
        <Title order={order} style={{ fontSize: 24, lineHeight: 1.3 }}>{title}</Title>
        <Text size="sm" color="dimmed">{description}</Text>
      </Stack>
      {action}
    </Group>
    {children && <details><summary style={{ cursor: 'pointer', marginTop: 12 }}>Hướng dẫn</summary>{children}</details>}
  </Paper>;
}
