<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { PermissionApi } from '#/api/system';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { isEqual, trimToNull } from '@vben/utils';

import { ElMessage, ElMessageBox } from 'element-plus';

import { confirmDiscardChanges } from '#/adapter/confirm-discard-changes';
import { useVbenForm } from '#/adapter/form';
import { createPermission, updatePermission } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

/** 权限创建或编辑草稿；继承权限字段，未填写字段允许省略。 */
type PermissionForm = Partial<
  Pick<
    PermissionApi.Permission,
    | 'code'
    | 'id'
    | 'name'
    | 'publicAccess'
    | 'remark'
    | 'targetIdentifier'
    | 'targetQualifier'
  >
>;

const initialValues = ref<PermissionForm>();
const initialPublicAccess = ref(false);
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
    label: '权限标识',
    rules: 'required',
  },
  {
    component: 'Input',
    fieldName: 'name',
    label: '权限名称',
    rules: 'required',
  },
  {
    component: 'Input',
    componentProps: {
      placeholder: '例如 POST',
    },
    fieldName: 'targetQualifier',
    label: '目标限定符',
  },
  {
    component: 'Input',
    fieldName: 'targetIdentifier',
    label: '访问目标标识',
    rules: 'required',
  },
  {
    component: 'Input',
    componentProps: { rows: 3, type: 'textarea' },
    fieldName: 'remark',
    formItemClass: 'col-span-full',
    label: '描述',
  },
  {
    component: 'Switch',
    defaultValue: false,
    description: '无需角色授权即可访问',
    fieldName: 'publicAccess',
    label: '公开访问',
  },
];

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: { class: 'w-full' },
    labelWidth: 130,
  },
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
    const values = await formApi.getValues<PermissionForm>();
    if (!initialPublicAccess.value && values.publicAccess) {
      try {
        await ElMessageBox.confirm(
          '开启后，该访问目标将绕过角色授权检查。确定公开访问吗？',
          '确认公开访问',
          { type: 'warning' },
        );
      } catch {
        return;
      }
    }
    trimToNull(values);
    drawerApi.lock();
    try {
      const data = {
        code: values.code ?? '',
        name: values.name ?? '',
        publicAccess: values.publicAccess ?? false,
        remark: values.remark,
        targetIdentifier: values.targetIdentifier ?? '',
        targetQualifier: values.targetQualifier,
      };
      await (values.id
        ? updatePermission({ id: values.id, ...data })
        : createPermission(data));
      saved.value = true;
      ElMessage.success('权限已保存');
      emit('success');
      await drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    saved.value = false;
    const row = drawerApi.getData<Partial<PermissionApi.Permission>>();
    initialPublicAccess.value = row.publicAccess ?? false;
    const values: PermissionForm = {
      code: row.code ?? '',
      id: row.id,
      name: row.name ?? '',
      publicAccess: row.publicAccess ?? false,
      remark: row.remark ?? '',
      targetIdentifier: row.targetIdentifier ?? '',
      targetQualifier: row.targetQualifier ?? '',
    };
    drawerApi.setState({
      title: row.id ? '修改权限' : '新增权限',
    });
    await formApi.reset();
    await nextTick();
    await formApi.setValues(values, false);
    initialValues.value = await formApi.getValues<PermissionForm>();
  },
});
</script>

<template>
  <Drawer class="w-full sm:max-w-2xl">
    <Form />
  </Drawer>
</template>
