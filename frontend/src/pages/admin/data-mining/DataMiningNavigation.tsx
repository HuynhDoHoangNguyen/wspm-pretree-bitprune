import React from 'react';
import { Stack, Text } from '@mantine/core';
import { NavLink } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import useDefaultNavbarStyles from 'components/DefaultNavbar/DefaultNavbar.styles';
import { hasDataMiningAdminAccess } from './access';
import { dataMiningPages } from './routes';

export default function DataMiningNavigation() {
  const { user, status, forbidden } = useSharedAuth();
  const { classes, cx } = useDefaultNavbarStyles();
  if (status !== 'authenticated' || forbidden || !hasDataMiningAdminAccess(user)) return null;

  return (
    <nav aria-label="Dữ liệu & Khai phá">
      <Stack spacing={0} my="sm">
        <Text size="xs" weight={600} color="dimmed" px="sm" py="xs">Dữ liệu &amp; Khai phá</Text>
        {dataMiningPages.map(({ path, label, Icon }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) => cx(classes.link, { [classes.linkActive]: isActive })}
          >
            <Icon className={classes.linkIcon} size={24}/>
            <span>{label}</span>
          </NavLink>
        ))}
      </Stack>
    </nav>
  );
}
