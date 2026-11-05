/** UI visibility only. The backend must independently authenticate and authorize. */
export function hasDataMiningAdminAccess(user: unknown): boolean {
  const roles = user && typeof user === 'object' ? (user as { roles?: unknown }).roles : null;
  return Array.isArray(roles) && roles.some(role => role === 'ADMIN');
}
