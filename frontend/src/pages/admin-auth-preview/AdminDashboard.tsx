import React, { useState } from 'react';
import { Alert, Badge, Button, Group, Stack, Text } from '@mantine/core';
import { useSearchParams } from 'react-router-dom';
import { adminNavigation, resolveAdminSection } from './AdminNavigation.config';
import type { AdminSection } from './AdminNavigation.config';
import AdminDashboardSidebar from './AdminDashboardSidebar';
import AdminContentRouter from './AdminContentRouter';
import './AdminLogin.css';

interface Props {
  identity: { displayName: string; username: string | null };
  onLogout: () => void;
  mode?: 'preview' | 'live';
  activeSection?: AdminSection;
  onSelectSection?: (section: AdminSection) => void;
  unavailableSections?: readonly AdminSection[];
  children?: React.ReactNode;
}

export default function AdminDashboard({ identity, onLogout, mode = 'preview', activeSection, onSelectSection, unavailableSections, children }: Props) {
  const [params, setParams] = useSearchParams();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const preview = mode === 'preview';
  const active = preview ? resolveAdminSection(params.get('section')) : activeSection;
  const item = adminNavigation.find(entry => entry.id === active);
  function select(section: AdminSection) {
    if (!preview) {
      onSelectSection?.(section);
      setMobileOpen(false);
      return;
    }
    const next = new URLSearchParams(params);
    next.set('section', section);
    setParams(next);
    setMobileOpen(false);
  }
  return <div className={`admin-dashboard-layout${collapsed ? ' sidebar-collapsed' : ''}`}>
    <Button className="admin-dashboard-mobile-toggle" variant="light" aria-controls={preview ? 'admin-preview-sidebar' : 'admin-sidebar'}
      aria-expanded={mobileOpen} onClick={() => setMobileOpen(value => !value)}>{mobileOpen ? 'Đóng menu Admin' : 'Mở menu Admin'}</Button>
    <AdminDashboardSidebar active={active} collapsed={collapsed} mobileOpen={mobileOpen} onSelect={select}
      onToggle={() => setCollapsed(value => !value)} onLogout={onLogout} preview={preview} unavailableSections={unavailableSections}/>
    <main aria-label="Nội dung Admin" className="admin-dashboard-content">
      <Stack spacing="lg">
        <Group position="apart"><Text size="sm" color="dimmed">ADMIN{item ? ' / ' + item.label : ''}</Text><Badge color="blue">{preview ? 'Quyền Admin · Mô phỏng' : 'Quyền Admin'}</Badge></Group>
        <Text size="sm">{identity.displayName} · {identity.username} · {preview ? 'Đã đăng nhập — mô phỏng' : 'Đã đăng nhập'}</Text>
        {preview && <Alert color="blue">Admin Layout Preview dùng fixture cục bộ, không gọi API và không mở khóa Admin production.</Alert>}
        {preview ? <AdminContentRouter section={resolveAdminSection(params.get('section'))}/> : children}
      </Stack>
    </main>
  </div>;
}
