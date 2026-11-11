import React, { useState } from 'react';
import { Anchor } from '@mantine/core';
import { useInRouterContext, useLocation, useNavigate } from 'react-router-dom';
import ComboPopup from './ComboPopup';

export default function ComboPopupLink({ comboId, productId, children }: {
  comboId: string; productId?: string; children?: React.ReactNode;
}) {
  const routed = useInRouterContext();
  return routed ? <RoutedLink comboId={comboId} productId={productId}>{children}</RoutedLink>
    : <LocalLink comboId={comboId} productId={productId}>{children}</LocalLink>;
}

type Props = { comboId: string; productId?: string; children?: React.ReactNode };
function RoutedLink({ comboId, productId, children }: Props) {
  const location = useLocation(), navigate = useNavigate();
  const [instance] = useState(() => Math.random().toString(36).slice(2));
  const opened = (location.state as { comboPopup?: string } | null)?.comboPopup === instance;
  const href = '/combos/' + encodeURIComponent(comboId) + (productId ? '?' + new URLSearchParams({ productId }) : '');
  return <>
    <Anchor href={href} onClick={(event: React.MouseEvent<HTMLAnchorElement>) => {
      if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      navigate(location.pathname + location.search + location.hash, { state: { ...(location.state as object ?? {}), comboPopup: instance } });
    }}>{children || 'Xem chi tiết Combo'}</Anchor>
    {opened && <ComboPopup comboId={comboId} contextProductId={productId} onClose={() => navigate(-1)}/>}
  </>;
}

function LocalLink({ comboId, productId, children }: Props) {
  const [opened, setOpened] = useState(false);
  const href = '/combos/' + encodeURIComponent(comboId) + (productId ? '?' + new URLSearchParams({ productId }) : '');
  return <>
    <Anchor href={href} onClick={(event: React.MouseEvent<HTMLAnchorElement>) => {
      if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      event.preventDefault(); setOpened(true);
    }}>{children || 'Xem chi tiết Combo'}</Anchor>
    {opened && <ComboPopup comboId={comboId} contextProductId={productId} onClose={() => setOpened(false)}/>}
  </>;
}
