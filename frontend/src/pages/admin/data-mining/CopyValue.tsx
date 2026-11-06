import React, { useEffect, useRef, useState } from 'react';
import { Button, Group, Text } from '@mantine/core';
import { Copy } from 'tabler-icons-react';

export default function CopyValue({ value, label }: { value: string; label: string }) {
  const [message, setMessage] = useState('');
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function copy() {
    try { await navigator.clipboard.writeText(value); if (mounted.current) setMessage('Đã sao chép.'); }
    catch { if (mounted.current) setMessage('Chưa thể sao chép tự động. Bạn có thể chọn và sao chép mã bên cạnh.'); }
  }
  return <div><Group spacing="xs" align="center">
    <Text size="sm" style={{ overflowWrap: 'anywhere', minWidth: 0 }}>{value}</Text>
    <Button variant="subtle" size="xs" leftIcon={<Copy size={14}/>} aria-label={'Sao chép ' + label} onClick={() => void copy()}>Sao chép</Button>
  </Group>{message && <Text size="sm" role="status">{message}</Text>}</div>;
}
