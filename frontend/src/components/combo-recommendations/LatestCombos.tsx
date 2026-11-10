import React, { useEffect, useState } from 'react';
import { Alert, Anchor, Badge, Button, Card, Group, Image, Stack, Text, Title } from '@mantine/core';
import { displayMoney, latestPublicCombos } from 'tv3/api/combos';
import { PublicCombo } from 'tv3/models/combos';
import { comboKindLabel } from './ComboSelection';

export default function LatestCombos() {
  const [state, setState] = useState<{ items?: PublicCombo[]; error?: boolean }>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true; const controller = new AbortController();
    latestPublicCombos(controller.signal).then(items => { if (active) setState({ items }); })
      .catch(() => { if (active && !controller.signal.aborted) setState({ error: true }); });
    return () => { active = false; controller.abort(); };
  }, [attempt]);
  if (state.items?.length === 0 && !state.error) return null;
  return <Stack spacing="sm" aria-label="Combo mới nhất">
    {state.items && <Title order={3} sx={theme => ({ fontSize: 18, color: theme.colors.blue[theme.colorScheme === 'dark' ? 3 : 7] })}>Combo mới nhất</Title>}
    {state.error ? <Alert color="yellow" title="Chưa tải được combo mới nhất">
      <Button variant="subtle" size="xs" onClick={() => { setState({}); setAttempt(n => n + 1); }}>Thử lại combo mới nhất</Button>
    </Alert> : !state.items
      ? <Text size="sm" color="blue">Đang tải combo mới nhất…</Text>
      : state.items.slice(0, 2).map(combo => <Card component="article" key={combo.comboId} withBorder p="sm" radius="md"
        sx={theme => ({ borderColor: theme.colors.blue[2], backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[7] : theme.colors.blue[0] })}>
        <Stack spacing="xs">
          <Badge size="sm">{comboKindLabel[combo.kind]}</Badge>
          <Anchor size="sm" weight={600} href={'/combos/' + encodeURIComponent(combo.comboId)}>{combo.name}</Anchor>
          {combo.items.map(i => <Group key={`${i.itemsetOrder}:${i.itemOrder}`} noWrap spacing="xs">
            <Image src={i.product.imageUrl || undefined} alt={i.product.name} width={40} height={40} fit="contain" withPlaceholder/>
            <Stack spacing={2} sx={{ minWidth: 0 }}>
              <Text size="xs" weight={500}>{i.product.name}</Text>
              <Text size="xs" color="pink" weight={600}>{displayMoney(i.product.currentPrice, combo.pricing.currencyCode)} × {i.quantity}</Text>
              {combo.kind !== 'SAME_ORDER_BUNDLE' && <Text size="xs" color="dimmed">Bước {i.itemsetOrder}</Text>}
            </Stack>
          </Group>)}
        </Stack>
      </Card>)}
  </Stack>;
}
