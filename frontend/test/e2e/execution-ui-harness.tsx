/** Test-only browser entry: actual UI/client/guard with a fixture identity, no real backend authentication. */
import React from 'react';
import ReactDOM from 'react-dom';
import { MantineProvider } from '@mantine/core';
import { BrowserRouter, Routes } from 'react-router-dom';
import { authSession } from '../../src/auth/session';
import { setWdmAuthAdapter } from '../../src/api/admin/data-mining';
import { createDataMiningRoutes } from '../../src/pages/admin/data-mining/routes';

const customer = new URLSearchParams(window.location.search).get('fixtureRole') === 'CUSTOMER';
authSession.useStore.setState({ status: 'authenticated', user: { userId: 'browser-fixture', username: 'fixture', email: null,
  displayName: 'Browser fixture', status: 'ACTIVE', roles: customer ? ['CUSTOMER'] : ['ADMIN'], emailVerified: false },
accessToken: 'fixture-only-token', forbidden: false, error: null });
setWdmAuthAdapter({ getAccessToken: () => 'fixture-only-token' });
ReactDOM.render(<MantineProvider withGlobalStyles withNormalizeCSS><BrowserRouter><Routes>{createDataMiningRoutes()}</Routes></BrowserRouter></MantineProvider>, document.getElementById('root'));
