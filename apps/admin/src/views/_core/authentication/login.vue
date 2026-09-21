<script lang="ts" setup>
import type { ExtendedFormApi, VbenFormSchema } from '@vben/common-ui';
import type { Recordable } from '@vben/types';

import { computed, markRaw, useTemplateRef } from 'vue';

import { AuthenticationLogin, SliderCaptcha, z } from '@vben/common-ui';

import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

interface AuthenticationLoginInstance {
  getFormApi: () => ExtendedFormApi;
}

const loginRef = useTemplateRef<AuthenticationLoginInstance>('loginRef');

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: '请输入用户名',
      },
      fieldName: 'username',
      label: '账号',
      rules: z.string().min(1, { message: '请输入用户名' }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: '密码',
      },
      fieldName: 'password',
      label: '密码',
      rules: z.string().min(1, { message: '请输入密码' }),
    },
    {
      component: markRaw(SliderCaptcha),
      fieldName: 'captcha',
      rules: z.boolean().refine((value) => value, {
        message: '请先完成验证',
      }),
    },
  ];
});

async function handleLogin(values: Recordable<any>) {
  try {
    const { userInfo } = await authStore.login(values);
    if (userInfo) {
      return;
    }
  } catch {
    // 登录流程未完成时在下方重置本地验证码；此分支并不能证明所有异常都是已展示的业务拒绝。
  }

  await loginRef.value?.getFormApi().setFieldValue('captcha', false);
}
</script>

<template>
  <AuthenticationLogin
    ref="loginRef"
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :show-code-login="false"
    :show-qrcode-login="false"
    :show-register="false"
    :show-third-party-login="false"
    @submit="handleLogin"
  />
</template>
