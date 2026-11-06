import React from 'react';
import { render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { useSharedAuth } from './session';
import IdentityPanel from './IdentityPanel';
jest.mock('./session', () => ({ useSharedAuth: jest.fn() }));
const identity = useSharedAuth as unknown as jest.Mock;
beforeEach(() => { Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
  addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) }); });
it.each(['ADMIN', 'CUSTOMER'])('shows shared %s identity and honest missing fields without edit actions', role => {
  identity.mockReturnValue({ user: { displayName: 'Nguyễn An', username: null, email: null, emailVerified: false, roles: [role], status: 'ACTIVE' } });
  render(<MantineProvider><IdentityPanel/></MantineProvider>);
  expect(screen.getByRole('heading', { name: 'Thông tin tài khoản' })).toBeVisible();
  expect(screen.getByText('Nguyễn An')).toBeVisible(); expect(screen.getByText('Chưa có email')).toBeVisible();
  expect(screen.getByText('Chưa có email để xác minh')).toBeVisible(); expect(screen.queryByRole('button')).not.toBeInTheDocument();
});
it('distinguishes an existing unverified email from a verified email', () => {
  const user = { displayName: 'An', username: 'an', email: 'an@example.test', emailVerified: false, roles: ['ADMIN'], status: 'ACTIVE' };
  identity.mockReturnValue({ user }); const view = render(<MantineProvider><IdentityPanel/></MantineProvider>);
  expect(screen.getByText('Chưa xác minh')).toBeVisible();
  identity.mockReturnValue({ user: { ...user, emailVerified: true } }); view.rerender(<MantineProvider><IdentityPanel/></MantineProvider>);
  expect(screen.getByText('Đã xác minh')).toBeVisible(); expect(screen.queryByText('Chưa xác minh')).not.toBeInTheDocument();
});
