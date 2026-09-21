<script setup lang="ts">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { PermissionApi } from '#/api/system';

import { Page, useVbenDrawer, VbenButton } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { ElMessage, ElTag } from 'element-plus';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { getPermissionList, removePermission } from '#/api/system';

import Form from './components/form.vue';
import { useColumns, useGridFormSchema } from './data';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid<PermissionApi.Permission>({
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
          const list = await getPermissionList(args);
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
  } as VxeTableGridOptions<PermissionApi.Permission>,
});

function onCreate() {
  formDrawerApi.setData({ publicAccess: false }).open();
}

function onEdit(row: PermissionApi.Permission) {
  if (!row.builtIn) formDrawerApi.setData(row).open();
}

async function onDelete(row: PermissionApi.Permission) {
  if (row.builtIn) return;
  await removePermission(row.id);
  ElMessage.success('权限已删除');
  await gridApi.query();
}

function refresh() {
  void gridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="refresh" />
    <Grid table-title="权限管理">
      <template #toolbar-tools>
        <VbenButton
          v-access:code="'system:permission:create'"
          size="sm"
          @click="onCreate"
        >
          <IconifyIcon icon="lucide:plus" class="mr-2 size-4" />
          新增权限
        </VbenButton>
      </template>

      <template #access="{ row }">
        <ElTag :type="row.publicAccess ? 'success' : 'info'" effect="plain">
          {{ row.publicAccess ? '公开访问' : '需要授权' }}
        </ElTag>
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
              auth: 'system:permission:update',
              disabled: row.builtIn,
              text: '修改',
              tooltip: row.builtIn ? '内置资源不可修改' : undefined,
              onClick: () => onEdit(row),
            },
          ]"
          :dropdown-actions="[
            {
              auth: 'system:permission:remove',
              danger: true,
              disabled: row.builtIn,
              text: '删除',
              popConfirm: {
                title: `确定删除权限“${row.name}”吗？`,
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
