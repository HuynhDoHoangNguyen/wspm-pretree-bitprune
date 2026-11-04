interface DataMiningIdentity {
  roles?: readonly { code?: string }[] | null;
}

/** UI visibility only. The backend must independently authenticate and authorize. */
export function hasDataMiningAdminAccess(user: DataMiningIdentity | null | undefined): boolean {
  const roles = user?.roles;
  return Array.isArray(roles) && roles.some(role => role?.code === 'ADMIN');
}
