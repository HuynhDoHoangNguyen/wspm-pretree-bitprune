import React, { useEffect, useRef, useState } from 'react';
import { Button, Group, Text } from '@mantine/core';
import { Copy } from 'tabler-icons-react';

export default function CopyValue({ value, label, displayValue = value, buttonText = 'Sao chép' }: {
  value: string; label: string; displayValue?: string; buttonText?: string;
}) {
  const [message, setMessage] = useState('');
  const [manual, setManual] = useState(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function copy() {
    try { await navigator.clipboard.writeText(value); if (mounted.current) { setMessage('Đã sao chép.'); setManual(false); } }
    catch { if (mounted.current) { setMessage('Chưa thể sao chép tự động. Bạn có thể chọn và sao chép nội dung hiển thị.'); setManual(true); } }
  }
  return <div><Group spacing="xs" align="center">
    <Text size="sm" style={{ overflowWrap: 'anywhere', minWidth: 0 }}>{displayValue}</Text>
    <Button variant="subtle" size="xs" leftIcon={<Copy size={14}/>} aria-label={'Sao chép ' + label} onClick={() => void copy()}>{buttonText}</Button>
  </Group>{message && <Text size="sm" role="status">{message}</Text>}
  {manual && displayValue !== value && <Text component="code" size="sm" style={{ display: 'block', overflowWrap: 'anywhere' }}>{value}</Text>}</div>;
}
