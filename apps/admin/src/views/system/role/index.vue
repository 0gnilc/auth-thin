<script setup lang="ts">
import type { ChecklistDrawerData } from '../components/checklist-drawer.vue';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { RoleApi } from '#/api/system';

import { Page, useVbenDrawer, VbenButton } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { ElMessage, ElTag } from 'element-plus';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import {
  getPermissionList,
  getRoleList,
  getRolePermissionIds,
  removeRole,
  saveRolePermissions,
} from '#/api/system';

import ChecklistDrawer from '../components/checklist-drawer.vue';
import Form from './components/form.vue';
import Menu from './components/menu.vue';
import { useColumns, useGridFormSchema } from './data';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [PermissionsDrawer, permissionsDrawerApi] = useVbenDrawer({
  connectedComponent: ChecklistDrawer,
  destroyOnClose: true,
});
const [MenusDrawer, menusDrawerApi] = useVbenDrawer({
  connectedComponent: Menu,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid<RoleApi.Role>({
  formOptions: { schema: useGridFormSchema(), submitOnChange: false },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        async query(_params, args) {
          const list = await getRoleList(args);
          return { list, total: list.length };
        },
      },
      showLoading: false,
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      custom: true,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<RoleApi.Role>,
});

function onCreate() {
  formDrawerApi.setData({}).open();
}

function onEdit(row: RoleApi.Role) {
  if (!row.builtIn) formDrawerApi.setData(row).open();
}

function onPermissions(row: RoleApi.Role) {
  if (row.builtIn) return;
  const data: ChecklistDrawerData = {
    async load() {
      const [permissions, selected] = await Promise.all([
        getPermissionList(),
        getRolePermissionIds(row.id),
      ]);
      return {
        options: permissions.map((permission) => ({
          description: permission.targetQualifier
            ? `${permission.targetQualifier} ${permission.targetIdentifier}`
            : permission.targetIdentifier,
          label: permission.name,
          value: permission.id,
        })),
        selected,
      };
    },
    async save(selected) {
      await saveRolePermissions(row.id, selected);
      ElMessage.success('角色权限已保存');
    },
    title: `为“${row.name}”分配权限`,
  };
  permissionsDrawerApi.setData(data).open();
}

function onMenus(row: RoleApi.Role) {
  if (!row.builtIn) menusDrawerApi.setData(row).open();
}

async function onDelete(row: RoleApi.Role) {
  if (row.builtIn) return;
  await removeRole(row.id);
  ElMessage.success('角色已删除');
  await gridApi.query();
}

function refresh() {
  void gridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="refresh" />
    <PermissionsDrawer />
    <MenusDrawer />
    <Grid table-title="角色管理">
      <template #toolbar-tools>
        <VbenButton
          v-access:code="'system:role:create'"
          size="sm"
          @click="onCreate"
        >
          <IconifyIcon icon="lucide:plus" class="mr-2 size-4" />
          新增角色
        </VbenButton>
      </template>

      <template #type="{ row }">
        <ElTag :type="row.builtIn ? 'warning' : 'info'" effect="plain">
          {{ row.builtIn ? '内置' : '自定义' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenTableAction
          :actions="[
            {
              auth: 'system:role:update',
              disabled: row.builtIn,
              text: '修改',
              tooltip: row.builtIn ? '内置资源不可修改' : undefined,
              onClick: () => onEdit(row),
            },
            {
              auth: 'system:role:manage-permissions',
              disabled: row.builtIn,
              text: '分配权限',
              onClick: () => onPermissions(row),
            },
            {
              auth: 'system:role:manage-menus',
              disabled: row.builtIn,
              text: '菜单授权',
              onClick: () => onMenus(row),
            },
          ]"
          :dropdown-actions="[
            {
              auth: 'system:role:remove',
              danger: true,
              disabled: row.builtIn,
              text: '删除',
              popConfirm: {
                title: `确定删除角色“${row.name}”吗？`,
                confirm: () => onDelete(row),
              },
            },
          ]"
          align="center"
        />
      </template>
    </Grid>
  </Page>
</template>
