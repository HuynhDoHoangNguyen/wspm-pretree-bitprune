import React from 'react';
import { ActionIcon, Button, Text, Tooltip } from '@mantine/core';
import { BRAND_NAME } from 'constants/Brand';
import { Activity, ChartBar, Database, FileBarcode, LayoutSidebarLeftCollapse, LayoutSidebarLeftExpand, List, Logout, Package, PlayerPlay, Settings } from 'tabler-icons-react';
import { adminNavigation } from './AdminNavigation.config';
import type { AdminSection } from './AdminNavigation.config';

const icons = [Database, Settings, PlayerPlay, List, Package, Activity, FileBarcode, ChartBar];

interface Props {
  active?: AdminSection;
  preview?: boolean;
  unavailableSections?: readonly AdminSection[];
  collapsed: boolean;
  mobileOpen: boolean;
  onSelect: (section: AdminSection) => void;
  onToggle: () => void;
  onLogout: () => void;
}

export default function AdminDashboardSidebar({ active, collapsed, mobileOpen, onSelect, onToggle, onLogout, preview = true, unavailableSections = [] }: Props) {
  const toggleLabel = collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng';
  const logoutLabel = preview ? 'Đăng xuất Preview' : 'Đăng xuất';
  return <aside id={preview ? 'admin-preview-sidebar' : 'admin-sidebar'} aria-label={preview ? 'Sidebar Admin Preview' : 'Sidebar Admin'} className={`admin-dashboard-sidebar${collapsed ? ' is-collapsed' : ''}${mobileOpen ? ' mobile-open' : ''}`}>
    <div className="admin-dashboard-brand">
      <div className="admin-dashboard-sidebar-header">
        {!collapsed && <Text weight={800} color="blue">{BRAND_NAME}</Text>}
        <Tooltip label={toggleLabel} position="right" withArrow>
          <ActionIcon type="button" variant="hover" color="blue" size={36} radius="md" className="admin-dashboard-collapse-toggle"
            onClick={onToggle} aria-expanded={!collapsed} aria-controls="admin-dashboard-menu" aria-label={toggleLabel}>
            {collapsed ? <LayoutSidebarLeftExpand size={20} aria-hidden="true" data-testid="sidebar-expand-icon"/>
              : <LayoutSidebarLeftCollapse size={20} aria-hidden="true" data-testid="sidebar-collapse-icon"/>}
          </ActionIcon>
        </Tooltip>
      </div>
      {!collapsed && <Text size="xs" color="dimmed">{preview ? 'ADMIN WORKSPACE · PREVIEW' : 'ADMIN WORKSPACE'}</Text>}
    </div>
    <nav id="admin-dashboard-menu" aria-label="Điều hướng Dashboard" className="admin-dashboard-menu">
      {adminNavigation.map((item, index) => {
        const Icon = icons[index];
        const unavailable = unavailableSections.includes(item.id);
        return <button key={item.id} type="button" className="admin-dashboard-menu-item"
          disabled={unavailable} aria-label={item.label} title={unavailable ? item.label + ' · Chưa khả dụng' : item.label} aria-current={active === item.id ? 'page' : undefined}
          onClick={() => onSelect(item.id)}>
          <span className="admin-dashboard-menu-mark" aria-hidden="true"><Icon size={20}/></span>
          {!collapsed && <span>{item.label}{unavailable && <Text component="span" size="xs" color="dimmed" style={{ display: 'block' }}>Chưa khả dụng</Text>}</span>}
        </button>;
      })}
    </nav>
    <Button color="red" variant="light" className="admin-dashboard-logout" onClick={onLogout}
      leftIcon={collapsed ? undefined : <Logout size={18} aria-hidden="true"/>}
      aria-label={logoutLabel} title={logoutLabel}>{collapsed ? <Logout size={18} aria-hidden="true"/> : logoutLabel}</Button>
  </aside>;
}
