import { useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import NotifyUtils from 'utils/NotifyUtils';
import { authMessage } from './api';
import { authSession } from './session';

/** Clear shared access through the session owner; both sites finish on the public home. */
export function useSignOut() {
  const navigate = useNavigate();
  const active = useRef(false);
  return async () => {
    if (active.current) return;
    active.current = true;
    try {
      await authSession.logout();
      NotifyUtils.simpleSuccess('Đăng xuất thành công');
    } catch (error) {
      NotifyUtils.simpleFailed('Đã xóa truy cập cục bộ; chưa xác nhận đăng xuất máy chủ. ' + authMessage(error));
    } finally {
      navigate('/', { replace: true });
      active.current = false;
    }
  };
}
