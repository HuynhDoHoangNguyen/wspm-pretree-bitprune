import React from 'react';
import { Badge, Group, Paper, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { Clock } from 'tabler-icons-react';
import useTitle from 'hooks/use-title';

interface PageFrameProps {
  title: string;
  description: string;
  availability: string;
}

export default function DataMiningPageFrame({ title, description, availability }: PageFrameProps) {
  useTitle(title);

  return (
    <Stack spacing="lg" mb={30}>
      <Group position="apart" align="flex-start">
        <Stack spacing={4}>
          <Text size="sm" color="dimmed">Dữ liệu &amp; Khai phá</Text>
          <Title order={2}>{title}</Title>
          <Text color="dimmed">{description}</Text>
        </Stack>
        <Badge color="gray" variant="light">Đang phát triển</Badge>
      </Group>
      <Paper withBorder radius="md" p="xl">
        <Group align="flex-start" noWrap>
          <ThemeIcon size={44} radius="md" color="blue" variant="light">
            <Clock size={24}/>
          </ThemeIcon>
          <Stack spacing="xs">
            <Title order={3}>Chức năng đang được chuẩn bị</Title>
            <Text color="dimmed">{availability}</Text>
          </Stack>
        </Group>
      </Paper>
    </Stack>
  );
}
