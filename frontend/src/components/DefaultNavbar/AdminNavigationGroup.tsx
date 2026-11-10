import React, { useEffect, useState } from 'react';
import { Box } from '@mantine/core';
import { Link, useLocation } from 'react-router-dom';
import { ChevronDown, ChevronRight } from 'tabler-icons-react';
import useAppStore from 'stores/use-app-store';
import useDefaultNavbarStyles from './DefaultNavbar.styles';

export function AdminNavigationLink({ link, label, active, disabled, icon }: {
  link: string; label: string; active: boolean; disabled?: boolean; icon?: React.ReactNode;
}) {
  const { classes, cx } = useDefaultNavbarStyles();
  const { closeNavbar } = useAppStore();
  return <Link to={link} aria-current={active ? 'page' : undefined} aria-disabled={disabled || undefined}
    className={cx(classes.link, { [classes.linkActive]: active, [classes.linkDisabled]: !!disabled })}
    onClick={() => { if (window.matchMedia('(max-width: 991px)').matches) closeNavbar(); }}>
    {icon}<span>{label}</span>
  </Link>;
}

export default function AdminNavigationGroup({ label, current, disabled, icon, children }: {
  label: string; current: boolean; disabled?: boolean; icon?: React.ReactNode; children: React.ReactNode;
}) {
  const location = useLocation();
  const [opened, setOpened] = useState(current);
  const { classes } = useDefaultNavbarStyles();
  useEffect(() => { if (current) setOpened(true); }, [current, location.pathname, location.search]);
  return <Box>
    <button type="button" className={classes.link} aria-expanded={opened} disabled={disabled}
      style={{ width: '100%', border: 0, background: 'transparent', cursor: 'pointer', textAlign: 'left' }}
      onClick={() => setOpened(value => !value)}>
      {icon}<span style={{ flex: 1 }}>{label}</span>{opened ? <ChevronDown size={16}/> : <ChevronRight size={16}/>}
    </button>
    <Box pl="sm" hidden={!opened}>{children}</Box>
  </Box>;
}
