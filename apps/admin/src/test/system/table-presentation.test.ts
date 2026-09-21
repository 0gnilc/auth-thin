import { describe, expect, it } from 'vitest';

import { useColumns as useAdminColumns } from '#/views/system/admin/data';
import {
  menuTypeTagTypes,
  useColumns as useMenuColumns,
} from '#/views/system/menu/data';
import { useColumns as usePermissionColumns } from '#/views/system/permission/data';
import { useColumns as useRoleColumns } from '#/views/system/role/data';

function operationWidth(columns: Array<Record<string, any>> | undefined) {
  return columns?.find(({ field }) => field === 'operation')?.width;
}

describe('system table presentation', () => {
  it('sizes action columns for text-only row actions', () => {
    expect(operationWidth(useAdminColumns())).toBe(200);
    expect(operationWidth(useRoleColumns())).toBe(280);
    expect(operationWidth(usePermissionColumns())).toBe(120);
    expect(operationWidth(useMenuColumns())).toBe(190);
  });

  it('assigns a distinct tag color to every menu type', () => {
    expect(menuTypeTagTypes).toEqual({
      button: 'danger',
      catalog: 'primary',
      embedded: 'success',
      link: 'info',
      menu: 'warning',
    });
  });
});
