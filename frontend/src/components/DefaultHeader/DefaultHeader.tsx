import React from 'react';
import { ActionIcon, Box, Burger, Button, createStyles, Group, Header, MediaQuery, useMantineColorScheme } from '@mantine/core';
import { Bell, Browser, Icon, Logout, Messages, MoonStars, Search, Sun, User } from 'tabler-icons-react';
import { Link } from 'react-router-dom';
import useAppStore from 'stores/use-app-store';
import { ElectroLogo } from 'components';
import { useSignOut } from 'auth/useSignOut';

const useStyles = createStyles((theme) => ({
  header: {
    paddingLeft: theme.spacing.md,
    paddingRight: theme.spacing.md,
  },

  inner: {
    height: 56,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: theme.spacing.xs,
  },

  logo: {
    display: 'flex',
    flexShrink: 0,
    [theme.fn.smallerThan('sm')]: { '& svg': { width: 86 } },
  },

  links: {
    [theme.fn.smallerThan('lg')]: {
      display: 'none',
    },
  },

  search: {
    [theme.fn.smallerThan('xs')]: {
      display: 'none',
    },
    width: 300,
  },

  link: {
    display: 'flex',
    alignItems: 'center',
    lineHeight: 1,
    padding: '8px 12px',
    borderRadius: theme.radius.sm,
    textDecoration: 'none',
    color: theme.colorScheme === 'dark' ? theme.colors.dark[0] : theme.colors.gray[7],
    fontSize: theme.fontSizes.sm,
    fontWeight: 500,

    '&:hover': {
      backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[6] : theme.colors.gray[0],
      color: theme.colorScheme === 'dark' ? theme.white : theme.black,
    },

    '&:active': {
      backgroundColor: theme.colors[theme.primaryColor][6],
      color: theme.white,
    },
  },
}));

interface HeaderLink {
  link: string;
  label: string;
  icon: Icon;
  target?: string;
}

const headerLinks: HeaderLink[] = [
  {
    link: '/admin/account',
    label: 'Tài khoản',
    icon: User,
  },
  {
    link: '/admin/notification',
    label: 'Thông báo',
    icon: Bell,
  },
  {
    link: '/admin/chat',
    label: 'Tin nhắn',
    icon: Messages,
  },
];

export function DefaultHeader() {
  const { classes } = useStyles();

  const { opened, toggleOpened } = useAppStore();
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();

  const handleSignoutButton = useSignOut();

  const dark = colorScheme === 'dark';

  const headerLinksFragment = headerLinks.map((headerLink) => (
    <Link
      key={headerLink.label}
      to={headerLink.link}
      target={headerLink.target}
      className={classes.link}
    >
      <headerLink.icon size={16} style={{ marginRight: 7.5 }}/>
      {headerLink.label}
    </Link>
  ));

  return (
    <Header height={56} className={classes.header}>
      <div className={classes.inner}>
        <Group spacing="xs" noWrap>
          <MediaQuery largerThan="md" styles={{ display: 'none' }}>
            <Burger opened={opened} onClick={toggleOpened} size="sm" aria-label={opened ? 'Đóng menu' : 'Mở menu'} aria-expanded={opened}/>
          </MediaQuery>
          <Box component={Link} to="/admin" className={classes.logo}>
            <ElectroLogo/>
          </Box>
        </Group>

        <Group spacing="xs" noWrap>
          <Group ml={50} spacing={5} className={classes.links}>
            {headerLinksFragment}
          </Group>
          <Group spacing={6} noWrap>
            <Button component={Link} to="/" variant="light" size="xs" leftIcon={<Browser size={16}/>}>Về cửa hàng</Button>
            <MediaQuery smallerThan="sm" styles={{ display: 'none' }}><ActionIcon
              variant="outline"
              title="Tìm kiếm"
              color="blue"
            >
              <Search size={18}/>
            </ActionIcon></MediaQuery>
            <ActionIcon
              variant="outline"
              title="Thay đổi chế độ màu"
              color={dark ? 'yellow' : 'blue'}
              onClick={() => toggleColorScheme()}
            >
              {dark ? <Sun size={18}/> : <MoonStars size={18}/>}
            </ActionIcon>
            <ActionIcon
              variant="outline"
              title="Đăng xuất"
              color="blue"
              onClick={handleSignoutButton}
            >
              <Logout size={18}/>
            </ActionIcon>
          </Group>
        </Group>
      </div>
    </Header>
  );
}
