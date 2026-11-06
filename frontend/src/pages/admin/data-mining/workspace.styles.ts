import { createStyles } from '@mantine/core';

export default createStyles(theme => {
  const dark = theme.colorScheme === 'dark';
  return {
    root: {
      maxWidth: 1280, margin: '0 auto', width: '100%', paddingBottom: 32,
      fontFamily: '"Segoe UI", Arial, sans-serif',
      color: dark ? theme.colors.gray[1] : theme.colors.gray[9],
      '& .mantine-Text-root': { lineHeight: 1.65, fontSize: 15, fontFamily: 'inherit' },
      '& .mantine-Title-root': { fontFamily: 'inherit' },
      '& [data-secondary]': { color: dark ? theme.colors.gray[4] : theme.colors.gray[7] },
      '& .mantine-Button-root': { minHeight: 40, fontSize: 14, fontFamily: 'inherit' },
      '& .mantine-Alert-root': { border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[3]}` },
      '& .data-metric': {
        padding: '14px 16px', border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[3]}`,
        borderRadius: 8, background: dark ? theme.colors.dark[6] : theme.colors.gray[0],
        minWidth: 0, overflowWrap: 'anywhere',
        '& > :last-child': { fontSize: 22, fontWeight: 700, fontVariantNumeric: 'tabular-nums' },
      },
      '& .data-metrics': {
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 170px), 1fr))', gap: 12,
        '@media (max-width: 600px)': { gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', '& .data-metric': { padding: 12 } },
      },
      '& .data-metrics-conversion': {
        gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
        '@media (max-width: 600px)': { gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' },
      },
      '& details > summary': { cursor: 'pointer', padding: '12px 0', fontWeight: 600, fontSize: 14 },
      '& pre': { padding: 16, border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[3]}`,
        background: dark ? theme.colors.dark[8] : theme.colors.gray[0], maxHeight: 360, overflow: 'auto', fontSize: 14 },
    },
    hero: {
      padding: '28px 30px', borderRadius: 12, border: `1px solid ${dark ? theme.colors.blue[8] : theme.colors.blue[2]}`,
      background: dark ? theme.colors.dark[6] : 'linear-gradient(120deg, #eff6ff 0%, #f0fdfa 100%)',
      '& h1, & h2': { letterSpacing: '-0.6px', lineHeight: 1.25 },
      '@media (max-width: 600px)': { padding: 20 },
    },
    grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 260px), 1fr))', gap: 16 },
    step: {
      display: 'flex', gap: 14, alignItems: 'flex-start', padding: 20,
      border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[3]}`, borderRadius: 10,
      background: dark ? theme.colors.dark[7] : theme.white,
    },
    number: {
      width: 34, height: 34, flexShrink: 0, borderRadius: 8, display: 'grid', placeItems: 'center',
      background: dark ? theme.colors.blue[9] : theme.colors.blue[0], color: dark ? theme.colors.blue[1] : theme.colors.blue[8], fontWeight: 700,
    },
    fields: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: 20, margin: 0,
      '& dd': { margin: 0, overflowWrap: 'anywhere', fontSize: 15 }, '& dt': { marginBottom: 4 } },
    summary: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 140px), 1fr))', gap: 16, margin: 0,
      '& dd': { margin: 0, overflowWrap: 'anywhere', fontSize: 14 }, '& dt': { marginBottom: 4 } },
    tabs: { display: 'flex', gap: 8, padding: 8, borderRadius: 10,
      border: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[3]}`, background: dark ? theme.colors.dark[8] : theme.colors.gray[0] },
    tab: {
      ...theme.fn.focusStyles(), flex: '1 1 0', minWidth: 0, display: 'flex', gap: 10, alignItems: 'center', justifyContent: 'center',
      minHeight: 54, padding: '12px 16px', border: '1px solid transparent', borderRadius: 6, cursor: 'pointer',
      background: 'transparent', color: dark ? theme.colors.gray[3] : theme.colors.gray[7], font: 'inherit', fontWeight: 600,
      '&:hover': { background: dark ? theme.colors.dark[5] : theme.white },
      '&[aria-selected="true"]': { background: dark ? theme.colors.blue[9] : theme.white,
        borderColor: dark ? theme.colors.blue[6] : theme.colors.blue[3], color: dark ? theme.white : theme.colors.blue[8], boxShadow: theme.shadows.xs },
      '@media (max-width: 600px)': { fontSize: 13, padding: '10px 4px', flexDirection: 'column', gap: 4 },
    },
    table: {
      overflow: 'auto', maxHeight: 480, border: `1px solid ${dark ? theme.colors.dark[3] : theme.colors.gray[4]}`, borderRadius: 0,
      '& table': { minWidth: 650, borderCollapse: 'separate', borderSpacing: 0, fontFamily: 'inherit' },
      '& table thead tr th': { position: 'sticky', top: 0, zIndex: 1, padding: '14px 16px',
        background: dark ? theme.colors.blue[9] : theme.colors.blue[1], color: dark ? theme.white : theme.colors.blue[9],
        borderBottom: `2px solid ${dark ? theme.colors.blue[5] : theme.colors.blue[3]}`, fontSize: 14, whiteSpace: 'nowrap' },
      '& table tbody tr td': { padding: '14px 16px', verticalAlign: 'top', borderRight: `1px solid ${dark ? theme.colors.dark[4] : theme.colors.gray[2]}` },
      '& tbody tr:nth-of-type(even)': { background: dark ? theme.colors.dark[6] : theme.colors.gray[0] },
      '& tbody tr:hover': { background: dark ? theme.colors.dark[5] : theme.colors.blue[0] },
    },
  };
});
