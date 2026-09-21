<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { AdminApi } from '#/api/system';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { isEqual, trimToNull } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { confirmDiscardChanges } from '#/adapter/confirm-discard-changes';
import { useVbenForm, z } from '#/adapter/form';
import { createAdmin, updateAdmin } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

/** 管理员创建或更新表单，区分当前操作者和其他管理员。 */
type AdminForm = {
  /** 头像 URL；未设置时为空字符串，提交前按接口规则处理。 */
  avatar: string;
  /** 正在编辑的是否为当前管理员，用于限制禁用当前账户等操作。 */
  currentAdmin: boolean;
  /** 管理员简介草稿；未填写时为空字符串。 */
  desc: string;
  /** 被编辑的管理员 ID；创建时省略。 */
  id?: string;
  /** 创建时的初始密码或更新时的新密码；更新留空不改变密码。 */
  password: string;
  /** 管理员账户是否启用。 */
  status: boolean;
} & Pick<AdminApi.Admin, 'homePath' | 'nickname' | 'username'>;

const initialValues = ref<AdminForm>();
const saved = ref(false);

const passwordRule = z
  .string()
  .max(32, { message: '密码最多 32 个字符' })
  .refine(
    (value) =>
      !value ||
      (value.length >= 8 &&
        !/\s/.test(value) &&
        /[a-z]/.test(value) &&
        /[A-Z]/.test(value) &&
        /\d/.test(value) &&
        /[^A-Za-z0-9]/.test(value)),
    { message: '密码需包含大小写字母、数字和特殊字符，且不能含空白' },
  );

const schema: VbenFormSchema<AdminForm>[] = [
  {
    component: 'Input',
    fieldName: 'id',
    hide: true,
  },
  {
    component: 'Input',
    fieldName: 'currentAdmin',
    hide: true,
  },
  {
    component: 'Input',
    fieldName: 'username',
    label: '用户名',
    rules: 'required',
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      passwordStrength: true,
      placeholder: '留空则不修改密码',
    },
    fieldName: 'password',
    label: '密码',
    rules: passwordRule,
  },
  {
    component: 'Input',
    fieldName: 'nickname',
    label: '昵称',
    rules: 'required',
  },
  {
    component: 'Input',
    defaultValue: '/dashboard',
    fieldName: 'homePath',
    label: '默认首页',
    rules: 'required',
  },
  {
    component: 'Input',
    fieldName: 'avatar',
    label: '头像 URL',
  },
  {
    component: 'Input',
    componentProps: { rows: 3, type: 'textarea' },
    fieldName: 'desc',
    formItemClass: 'col-span-full',
    label: '描述',
  },
  {
    component: 'Switch',
    defaultValue: true,
    dependencies: {
      resolve: ({ values }) => ({
        componentProps: { disabled: Boolean(values.currentAdmin) },
      }),
      triggerFields: ['currentAdmin'],
    },
    fieldName: 'status',
    label: '启用状态',
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
    const values = await formApi.getValues<AdminForm>();
    if (!values.id && !values.password) {
      ElMessage.error('新增后台管理员时必须设置密码');
      return;
    }

    drawerApi.lock();
    try {
      trimToNull(values, 'password');
      await (values.id
        ? updateAdmin({
            avatar: values.avatar === '' ? null : values.avatar,
            desc: values.desc === '' ? null : values.desc,
            homePath: values.homePath,
            id: values.id,
            nickname: values.nickname,
            password: values.password || undefined,
            status: values.status,
            username: values.username,
          })
        : createAdmin({
            avatar: values.avatar === '' ? null : values.avatar,
            desc: values.desc === '' ? null : values.desc,
            homePath: values.homePath ?? '/dashboard',
            nickname: values.nickname ?? '',
            password: values.password,
            status: values.status ?? true,
            username: values.username ?? '',
          }));
      saved.value = true;
      ElMessage.success('后台管理员已保存');
      emit('success');
      await drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    saved.value = false;
    const row = drawerApi.getData<Partial<AdminForm>>();
    const values: AdminForm = {
      avatar: row.avatar ?? '',
      currentAdmin: row.currentAdmin ?? false,
      desc: row.desc ?? '',
      homePath: row.homePath ?? '/dashboard',
      id: row.id,
      nickname: row.nickname ?? '',
      password: '',
      status: row.status ?? true,
      username: row.username ?? '',
    };
    drawerApi.setState({
      title: row.id ? '修改后台管理员' : '新增后台管理员',
    });
    await formApi.reset();
    await nextTick();
    await formApi.setValues(values, false);
    initialValues.value = await formApi.getValues<AdminForm>();
  },
});
</script>

<template>
  <Drawer class="w-full sm:max-w-2xl">
    <Form />
  </Drawer>
</template>
