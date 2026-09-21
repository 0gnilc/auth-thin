<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { RoleApi } from '#/api/system';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { isEqual, trimToNull } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { confirmDiscardChanges } from '#/adapter/confirm-discard-changes';
import { useVbenForm } from '#/adapter/form';
import { createRole, updateRole } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

/** 角色创建或编辑草稿；继承角色字段，未填写字段允许省略。 */
type RoleForm = Partial<Pick<RoleApi.Role, 'code' | 'id' | 'name' | 'remark'>>;

const initialValues = ref<RoleForm>();
const saved = ref(false);

const schema: VbenFormSchema[] = [
  {
    component: 'Input',
    fieldName: 'id',
    hide: true,
  },
  {
    component: 'Input',
    fieldName: 'code',
    label: '角色标识',
    rules: 'required',
  },
  {
    component: 'Input',
    fieldName: 'name',
    label: '角色名称',
    rules: 'required',
  },
  {
    component: 'Input',
    componentProps: { rows: 4, type: 'textarea' },
    fieldName: 'remark',
    label: '描述',
  },
];

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' } },
  schema,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1',
});

const [Drawer, drawerApi] = useVbenDrawer({
  async onBeforeClose() {
    if (saved.value) return true;
    return confirmDiscardChanges(
      !isEqual(await formApi.getValues(), initialValues.value),
    );
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues<RoleForm>();
    trimToNull(values);
    drawerApi.lock();
    try {
      const data = {
        code: values.code ?? '',
        name: values.name ?? '',
        remark: values.remark,
      };
      await (values.id
        ? updateRole({ id: values.id, ...data })
        : createRole(data));
      saved.value = true;
      ElMessage.success('角色已保存');
      emit('success');
      await drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    saved.value = false;
    const row = drawerApi.getData<Partial<RoleApi.Role>>();
    const values: RoleForm = {
      code: row.code ?? '',
      id: row.id,
      name: row.name ?? '',
      remark: row.remark ?? '',
    };
    drawerApi.setState({
      title: row.id ? '修改角色' : '新增角色',
    });
    await formApi.reset();
    await nextTick();
    await formApi.setValues(values, false);
    initialValues.value = await formApi.getValues<RoleForm>();
  },
});
</script>

<template>
  <Drawer class="w-full sm:max-w-xl">
    <Form />
  </Drawer>
</template>
