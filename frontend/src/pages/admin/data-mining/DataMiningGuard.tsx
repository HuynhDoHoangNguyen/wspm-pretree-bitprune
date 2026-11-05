import React, { ReactNode } from 'react';
import { Alert } from '@mantine/core';
import { Lock } from 'tabler-icons-react';
import { useSharedAuth } from 'auth/session';
import AuthGate from 'auth/AuthGate';
import { hasDataMiningAdminAccess } from './access';

export default function DataMiningGuard({ children }: { children: ReactNode }) {
  const { user, status, forbidden } = useSharedAuth();
  if (['bootstrapping', 'unavailable', 'signing-out'].includes(status)) return <AuthGate/>;

  if (!user) {
    return (
      <Alert role="alert" icon={<Lock size={20}/>} title="Cần đăng nhập" color="blue">
        Vui lòng đăng nhập tài khoản quản trị để truy cập dữ liệu khai phá.
      </Alert>
    );
  }

  if (forbidden || !hasDataMiningAdminAccess(user)) {
    return (
      <Alert role="alert" icon={<Lock size={20}/>} title="Không có quyền truy cập" color="red">
        Tài khoản hiện tại không có quyền Admin để truy cập dữ liệu khai phá.
      </Alert>
    );
  }

  return <>{children}</>;
}
