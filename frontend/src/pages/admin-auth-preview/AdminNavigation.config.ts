// Integration mapping only. Proposed production paths do not register or change routes.
export const adminNavigation = [
  { id: 'data-preparation', label: 'Chuẩn bị dữ liệu', useCase: 'UC-ADM-02', productionPath: '/admin/data-preparation', routeStatus: 'proposed' },
  { id: 'mining-config', label: 'Cấu hình tham số', useCase: 'UC-ADM-03', productionPath: '/admin/mining-config', routeStatus: 'proposed' },
  { id: 'mining-execution', label: 'Chạy khai phá và kết quả', useCase: 'UC-ADM-04', productionPath: '/admin/mining-execution', routeStatus: 'proposed' },
  { id: 'patterns', label: 'Quản lý Pattern', useCase: 'UC-ADM-06', productionPath: '/admin/patterns', routeStatus: 'proposed' },
  { id: 'combos', label: 'Quản lý Combo', useCase: 'UC-ADM-07, UC-ADM-08', productionPath: '/admin/combos', routeStatus: 'proposed' },
  { id: 'mining-monitoring', label: 'Giám sát Mining', useCase: 'UC-ADM-05', productionPath: '/admin/mining-monitoring', routeStatus: 'proposed' },
  { id: 'mining-runs', label: 'Lịch sử RUN', useCase: 'UC-ADM-09', productionPath: '/admin/mining-runs', routeStatus: 'existing' },
  { id: 'mining-comparison', label: 'So sánh thuật toán', useCase: 'UC-ADM-09', productionPath: '/admin/mining-comparison', routeStatus: 'proposed' },
] as const;

export type AdminSection = typeof adminNavigation[number]['id'];
export function resolveAdminSection(value: string | null): AdminSection {
  return adminNavigation.find(item => item.id === value)?.id || 'data-preparation';
}
