import { createStyles } from '@mantine/core';

export default createStyles(theme => {
  const dark = theme.colorScheme === 'dark';
  return {
    navigation: { display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' },
    header: {
      padding: 28, borderRadius: 16, border: `1px solid ${dark ? theme.colors.blue[8] : theme.colors.blue[2]}`,
      background: dark ? theme.colors.dark[6] : 'linear-gradient(115deg, #eff6ff 0%, #f8fbff 58%, #f0fdfa 100%)',
      '@media (max-width: 600px)': { padding: 20 },
    },
    title: { fontSize: 28, lineHeight: 1.3, letterSpacing: '-0.6px', '@media (max-width: 600px)': { fontSize: 23 } },
    metadata: { display: 'grid', gridTemplateColumns: 'minmax(0, 1.2fr) minmax(0, 1fr)', gap: 16,
      '@media (max-width: 760px)': { gridTemplateColumns: 'minmax(0, 1fr)' } },
    tile: {
      minWidth: 0, padding: '16px 18px', borderRadius: 10,
      border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.blue[1]}`,
      background: dark ? theme.colors.dark[7] : 'rgba(255,255,255,0.88)',
      '& time': { fontWeight: 600, fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' },
    },
    related: {
      display: 'flex', gap: 16, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap',
      padding: '16px 20px', borderRadius: 12, border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[3]}`,
      background: dark ? theme.colors.dark[7] : theme.white,
    },
    relatedLabel: { display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 },
    icon: { display: 'grid', placeItems: 'center', width: 40, height: 40, flexShrink: 0, borderRadius: 10,
      color: dark ? theme.colors.blue[2] : theme.colors.blue[7], background: dark ? theme.colors.dark[5] : theme.colors.blue[0] },
    actions: { display: 'flex', gap: 10, flexWrap: 'wrap',
      '@media (max-width: 600px)': { width: '100%', '& > *': { flex: '1 1 100%' } } },
  };
});
