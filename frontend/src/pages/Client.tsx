import React from 'react';
import { Link, matchPath, Outlet, useLocation } from 'react-router-dom';
import { ActionIcon, Affix, Anchor, Button, Card, Group, Text, Tooltip, useMantineColorScheme } from '@mantine/core';
import { CustomerStoreLayout } from './customer-combos/CustomerCombos';
import { ClientFooter, ClientHeader, LoadingMiddleware } from 'components';
import { Messages, MoonStars, Sun } from 'tabler-icons-react';
import { useDisclosure, useHotkeys } from '@mantine/hooks';
import { useIsFetching } from 'react-query';
import useAuthStore from 'stores/use-auth-store';
import { useSharedAuth } from 'auth/session';
import { useSignOut } from 'auth/useSignOut';

function Client() {
  const isLoading = useIsFetching();

  const { user } = useAuthStore();
  const { pathname } = useLocation();
  const { user: identity } = useSharedAuth();
  const signOut = useSignOut();

  // TV3 pages share the original store shell without mounting legacy API widgets.
  if (['/', '/signin', '/signup', '/forgot', '/change-password'].includes(pathname) || matchPath('/products/:productId/combos', pathname) || matchPath('/combos/:comboId', pathname)) {
    return <CustomerStoreLayout actions={<Group>{identity ? <>
      <Text size="sm">{identity.displayName}</Text>
      <Button variant="light" color="blue" onClick={() => void signOut()}>Đăng xuất</Button>
    </> : <Button component={Link} to="/signin" variant="light" color="blue">Đăng nhập</Button>}</Group>}><Outlet/></CustomerStoreLayout>;
  }

  return (
    <>
      <LoadingMiddleware isLoading={!!isLoading}>
        <ClientHeader/>
        <Outlet/>
        <ClientFooter/>
      </LoadingMiddleware>
      {user && <ChatButton/>}
      <Shortcut/>
    </>
  );
}

function ChatButton() {
  return (
    <Affix position={{ bottom: 20, right: 20 }}>
      <Card shadow="sm" p="xs" radius="xl">
        <Tooltip
          label="Yêu cầu tư vấn mua hàng"
          position="left"
          placement="center"
          withArrow
        >
          <ActionIcon
            component={Link}
            to="/user/chat"
            color="teal"
            size="xl"
            radius="xl"
            variant="light"
          >
            <Messages/>
          </ActionIcon>
        </Tooltip>
      </Card>
    </Affix>
  );
}

// Only for test
function Shortcut() {
  const { user } = useSharedAuth();
  const signOut = useSignOut();
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const dark = colorScheme === 'dark';

  const [opened, handlers] = useDisclosure(false);

  useHotkeys([
    ['alt+Q', () => handlers.toggle()],
  ]);


  return (
    <Affix position={{ bottom: 20, right: 100 }} sx={{ display: opened ? 'block' : 'none' }}>
      <Card shadow="sm" p="sm">
        <Group>
          <Anchor component={Link} to="/">Client</Anchor>
          {user?.roles.includes('ADMIN') && <Anchor component={Link} to="/admin">Admin</Anchor>}
          {user && <Button color="teal" variant="light" compact onClick={() => void signOut()}>
            Đăng xuất
          </Button>}
          <ActionIcon
            size="sm"
            variant="outline"
            color={dark ? 'yellow' : 'blue'}
            onClick={() => toggleColorScheme()}
            title="Thay đổi chế độ màu"
          >
            {dark ? <Sun size={14}/> : <MoonStars size={14}/>}
          </ActionIcon>
        </Group>
      </Card>
    </Affix>
  );
}

export default Client;
