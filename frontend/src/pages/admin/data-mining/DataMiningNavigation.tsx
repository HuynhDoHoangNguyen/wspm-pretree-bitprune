import React from 'react';
import { Activity, ChartBar, Database, FileUpload, History, ListSearch, Package, PlayerPlay, Plus, User } from 'tabler-icons-react';
import useDefaultNavbarStyles from 'components/DefaultNavbar/DefaultNavbar.styles';
import { useLocation } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import AdminNavigationGroup, { AdminNavigationLink } from 'components/DefaultNavbar/AdminNavigationGroup';
import { hasDataMiningAdminAccess } from './access';

export function activeMiningItem(pathname: string, search: string): string {
  const path = pathname.replace(/\/+$/, '').toLowerCase();
  const params = new URLSearchParams(search);
  if (path === '/admin/datasets') return params.get('tab') === 'create' ? 'dataset-create' : 'datasets';
  if (path.startsWith('/admin/datasets/')) return 'datasets';
  if (path === '/admin/mining-runs') return params.get('tab') === 'history' ? 'history' : 'run-create';
  if (path.startsWith('/admin/mining-runs/') || path === '/admin/mining-run-history') return 'history';
  if (path === '/admin/mining-monitoring') return 'monitoring';
  if (path === '/admin/pattern-explorer' || path === '/admin/patterns' || path.startsWith('/admin/patterns/')) return 'patterns';
  if (path === '/admin/combos') return params.get('tab') === 'generate' || params.has('runId') ? 'combo-create' : 'combos';
  if (path === '/admin/mining-comparison') return 'comparison';
  if (path === '/admin/account') return 'account';
  return '';
}

const groups = [
  { label: 'Dữ liệu khai phá', items: [
    { id: 'datasets', Icon: Database, label: 'Danh sách dữ liệu', link: '/admin/datasets' },
    { id: 'dataset-create', Icon: FileUpload, label: 'Tạo dữ liệu mới', link: '/admin/datasets?tab=create' },
  ] },
  { label: 'Lần chạy khai phá', items: [
    { id: 'run-create', Icon: PlayerPlay, label: 'Tạo lần chạy', link: '/admin/mining-runs' },
    { id: 'history', Icon: History, label: 'Lịch sử lần chạy', link: '/admin/mining-runs?tab=history' },
    { id: 'monitoring', Icon: Activity, label: 'Giám sát thực thi', link: '/admin/mining-monitoring' },
  ] },
  { label: 'Mẫu khai phá', items: [{ id: 'patterns', Icon: ListSearch, label: 'Tra cứu Pattern', link: '/admin/patterns' }] },
  { label: 'Combo sản phẩm', items: [
    { id: 'combo-create', Icon: Plus, label: 'Tạo đề xuất Combo', link: '/admin/combos?tab=generate' },
    { id: 'combos', Icon: Package, label: 'Danh sách batch & duyệt', link: '/admin/combos' },
  ] },
];

export default function DataMiningNavigation() {
  const { user, status, forbidden } = useSharedAuth();
  const { pathname, search } = useLocation();
  const { classes } = useDefaultNavbarStyles();
  if (status !== 'authenticated' || forbidden || !hasDataMiningAdminAccess(user)) return null;
  const active = activeMiningItem(pathname, search);
  function destination(link: string) {
    const [path, query = ''] = link.split('?');
    if (pathname.replace(/\/+$/, '').toLowerCase() !== path || !['/admin/datasets', '/admin/mining-runs'].includes(path)) return link;
    const next = new URLSearchParams(search);
    const tab = new URLSearchParams(query).get('tab');
    if (tab) next.set('tab', tab); else next.delete('tab');
    return path + (next.toString() ? '?' + next : '');
  }
  return <nav aria-label="Dữ liệu & Khai phá">
    {groups.map(group => <AdminNavigationGroup key={group.label} label={group.label} current={group.items.some(item => item.id === active)} icon={React.createElement(group.items[0].Icon, { className: classes.linkIcon, size: 22, 'aria-hidden': true })}>
      {group.items.map(item => <AdminNavigationLink key={item.id} {...item} link={destination(item.link)} icon={<item.Icon className={classes.linkIcon} size={18} aria-hidden/>} active={item.id === active}/>)}
    </AdminNavigationGroup>)}
    <AdminNavigationLink link="/admin/mining-comparison" label="So sánh thuật toán" icon={<ChartBar className={classes.linkIcon} size={22} aria-hidden/>} active={active === 'comparison'}/>
    <AdminNavigationLink link="/admin/account" label="Tài khoản" icon={<User className={classes.linkIcon} size={22} aria-hidden/>} active={active === 'account'}/>
  </nav>;
}
