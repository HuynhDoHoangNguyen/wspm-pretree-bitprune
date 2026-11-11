import React from 'react';
import { useNavigate } from 'react-router-dom';
import { isComboIdentifier } from 'api/recommendation/combos';
import ComboPopup from 'components/recommendation/ComboPopup';

export default function ComboDetailPage({ comboId, productId }: { comboId: string; productId?: string | null }) {
  const navigate = useNavigate();
  const backHref = productId && isComboIdentifier(productId) ? '/products/' + encodeURIComponent(productId) : '/products';
  return <ComboPopup comboId={comboId} contextProductId={productId && isComboIdentifier(productId) ? productId : undefined} backHref={backHref} onClose={() => navigate(backHref, { replace: true })}/>;
}
