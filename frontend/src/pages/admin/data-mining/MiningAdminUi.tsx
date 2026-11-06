import React from 'react';
import { createStyles, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { Database } from 'tabler-icons-react';

// Presentation shared only by Pattern Explorer and Combo Admin; theme remains owned by App.
export const useMiningAdminStyles = createStyles(theme => ({
  page: { width: '100%', maxWidth: 1400, minWidth: 0, margin: '0 auto', paddingBottom: 32,
    '& input, & select, & textarea': { fontSize: 14 },
    '& button': { maxWidth: '100%' } },
  card: { minWidth: 0, padding: 24, borderRadius: theme.radius.md,
    [theme.fn.smallerThan('sm')]: { padding: 16 } },
  header: { minWidth: 0, marginBottom: 4 },
  wrap: { overflowWrap: 'anywhere', minWidth: 0 },
  code: { fontFamily: theme.fontFamilyMonospace, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap', lineHeight: 1.7 },
  scroll: { width: '100%', minWidth: 0, overflowX: 'auto', borderRadius: theme.radius.sm,
    '&:focus-visible': { outline: `2px solid ${theme.colors[theme.primaryColor][6]}`, outlineOffset: 2 } },
  table: { minWidth: 580, '& th': { whiteSpace: 'nowrap' }, '& td': { verticalAlign: 'top', paddingTop: 14, paddingBottom: 14 },
    '& tr[data-selected=true]': { backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[5] : theme.colors[theme.primaryColor][0] } },
  rowButton: { height: 'auto', minHeight: 36, padding: '6px 8px', textAlign: 'left', maxWidth: 360,
    '& .mantine-Button-label': { display: 'block', whiteSpace: 'normal', overflowWrap: 'anywhere' } },
  itemset: { minWidth: 0, margin: 0, padding: 16, border: `1px solid ${theme.colorScheme === 'dark' ? theme.colors.dark[4] : theme.colors.gray[3]}`,
    borderRadius: theme.radius.sm, backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[6] : theme.colors.gray[0],
    '& legend': { fontSize: 13, fontWeight: 600, padding: '0 6px' } },
  dictionary: { maxHeight: 260, overflowY: 'auto', minWidth: 0, padding: 12, borderRadius: theme.radius.sm,
    border: `1px solid ${theme.colorScheme === 'dark' ? theme.colors.dark[4] : theme.colors.gray[3]}` },
  tag: { maxWidth: '100%', height: 'auto', paddingTop: 5, paddingBottom: 5, textTransform: 'none',
    '& .mantine-Badge-inner': { whiteSpace: 'normal', overflowWrap: 'anywhere' } },
  footer: { borderTop: `1px solid ${theme.colorScheme === 'dark' ? theme.colors.dark[4] : theme.colors.gray[2]}`, paddingTop: 16 },
}));

export function MiningEmptyState({ title, children }: { title: string; children?: React.ReactNode }) {
  return <Stack align="center" spacing={12} py={32} sx={{ textAlign: 'center', overflowWrap: 'anywhere' }}>
    <ThemeIcon size={48} radius="xl" variant="light"><Database size={24} aria-hidden="true"/></ThemeIcon>
    <Title order={4}>{title}</Title>
    {children && <Text size="sm" color="dimmed" sx={{ maxWidth: 480 }}>{children}</Text>}
  </Stack>;
}
