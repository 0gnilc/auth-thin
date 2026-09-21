<script setup lang="ts">
import type { ElTree } from 'element-plus';

import type { MenuApi, RoleApi } from '#/api/system';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { isEqual } from '@vben/utils';

import { ElMessage, ElTree as ElTreeComponent } from 'element-plus';

import { confirmDiscardChanges } from '#/adapter/confirm-discard-changes';
import { getMenuTree, getRoleMenuIds, saveRoleMenus } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

const treeRef = ref<InstanceType<typeof ElTree>>();
const treeData = ref<MenuApi.Menu[]>([]);
const initialSelected = ref<string[]>([]);
const currentSelected = ref<string[]>([]);
const role = ref<RoleApi.Role>();
const saved = ref(false);

function normalize(values: Array<number | string>) {
  return [...new Set(values.map(String))].toSorted();
}

function readCheckedKeys() {
  return normalize((treeRef.value?.getCheckedKeys(false) ?? []) as string[]);
}

function onCheck() {
  currentSelected.value = readCheckedKeys();
}

const [Drawer, drawerApi] = useVbenDrawer({
  async onBeforeClose() {
    if (saved.value) return true;
    return confirmDiscardChanges(
      !isEqual(currentSelected.value, initialSelected.value),
    );
  },
  async onConfirm() {
    if (!role.value) return;
    drawerApi.lock();
    try {
      const menuIds = readCheckedKeys();
      await saveRoleMenus(role.value.id, menuIds);
      initialSelected.value = menuIds;
      currentSelected.value = menuIds;
      saved.value = true;
      ElMessage.success('角色菜单已保存');
      emit('success');
      await drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    saved.value = false;
    role.value = drawerApi.getData<RoleApi.Role>();
    drawerApi.setState({
      loading: true,
      title: `为“${role.value.name}”进行菜单授权`,
    });
    try {
      const [menus, selected] = await Promise.all([
        getMenuTree(),
        getRoleMenuIds(role.value.id),
      ]);
      treeData.value = menus;
      initialSelected.value = normalize(selected);
      currentSelected.value = normalize(selected);
      await nextTick();
      treeRef.value?.setCheckedKeys([]);
      // 回显显式保存的授权节点，不级联勾选其后代，避免打开表单就扩大原菜单授权。
      for (const id of selected) {
        treeRef.value?.setChecked(String(id), true, false);
      }
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
</script>

<template>
  <Drawer class="w-full sm:max-w-2xl" content-class="min-h-0 overflow-auto">
    <ElTreeComponent
      ref="treeRef"
      :data="treeData"
      node-key="id"
      show-checkbox
      check-on-click-node
      default-expand-all
      :props="{ children: 'children', label: 'title' }"
      @check="onCheck"
    >
      <template #default="{ data }">
        <span class="flex min-w-0 items-center gap-2">
          <span class="truncate">{{ data.title }}</span>
          <code class="text-xs text-muted-foreground">{{ data.name }}</code>
        </span>
      </template>
    </ElTreeComponent>
  </Drawer>
</template>
