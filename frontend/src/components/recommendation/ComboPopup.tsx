import BackButton from 'components/BackButton/BackButton';
import React, { useEffect, useState } from 'react';
import { Alert, Button, Modal, Stack, Text } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { authSession, useSharedAuth } from 'auth/session';
import { ComboApiError, personalCombo, readPublicCombo } from 'api/recommendation/combos';
import { PublicCombo } from 'models/recommendation/combos';
import ComboProducts from './ComboProducts';

export default function ComboPopup({ comboId, onClose, backHref, contextProductId }: {
  comboId: string; onClose: () => void; backHref?: string; contextProductId?: string;
}) {
  const { status, accessToken } = useSharedAuth();
  const token = status === 'authenticated' ? accessToken : null;
  const mobile = useMediaQuery('(max-width: 600px)');
  const [attempt, setAttempt] = useState(0);
  const binding = JSON.stringify([comboId, token, attempt, contextProductId]);
  const [state, setState] = useState<{ binding: string; combo?: PublicCombo; suggested?: string[]; error?: 'missing' | 'failed' }>({ binding: '' });
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const clear = authSession.onClear(() => { active = false; controller.abort(); setState({ binding: '' }); onClose(); });
    setState({ binding });
    async function load() {
      try {
        let combo: PublicCombo, suggested: string[] | undefined;
        try { combo = await readPublicCombo(comboId, controller.signal); }
        catch (error) {
          if (!(error instanceof ComboApiError) || error.status !== 404 || !token) throw error;
          const result = await personalCombo(token, comboId, controller.signal);
          combo = result.items[0].combo; suggested = result.items[0].suggestedProductIds;
        }
        if (active && !controller.signal.aborted) setState({ binding, combo, suggested });
      } catch (error) {
        if (!active || controller.signal.aborted) return;
        if (error instanceof ComboApiError && error.status === 401) { authSession.unauthorized(); return; }
        setState({ binding, error: error instanceof ComboApiError && error.status === 404 ? 'missing' : 'failed' });
      }
    }
    void load();
    return () => { active = false; controller.abort(); clear(); };
    // The close callback must not restart the request when a parent rerenders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [binding, comboId, token]);
  const current = state.binding === binding;
  const combo = current ? state.combo : undefined;
  return <Modal opened onClose={onClose} title={combo?.name || 'Chi tiết Combo'} size={mobile ? '100%' : 'lg'}
    styles={{ modal: { maxWidth: 'calc(100vw - 24px)' }, title: { fontWeight: 600, fontSize: 20 } }} overflow="inside"
    centered closeButtonLabel="Đóng chi tiết Combo" transitionDuration={0}>
    <Stack spacing="sm" style={{ overflowWrap: 'anywhere' }}>
      {backHref && <BackButton href={backHref}>Về sản phẩm</BackButton>}
      {(!current || (!combo && !state.error)) && <Text role="status">Đang tải chi tiết Combo…</Text>}
      {current && state.error === 'missing' && <Text role="status">Combo không khả dụng hoặc chưa phù hợp lịch sử mua của bạn. Đăng nhập để xem gợi ý cá nhân.</Text>}
      {current && state.error === 'failed' && <Alert color="red" title="Không tải được chi tiết Combo">
        <Button variant="subtle" onClick={() => setAttempt(n => n + 1)}>Thử lại</Button>
      </Alert>}
      {combo && <>
        {state.suggested && <Text size="sm" color="teal">Gợi ý cho bạn · Chọn sản phẩm cho bước mua tiếp theo.</Text>}
        <ComboProducts key={binding} combo={combo} suggestedProductIds={state.suggested} contextProductId={contextProductId}/>
      </>}
      <Button variant="default" onClick={onClose}>Đóng</Button>
    </Stack>
  </Modal>;
}
