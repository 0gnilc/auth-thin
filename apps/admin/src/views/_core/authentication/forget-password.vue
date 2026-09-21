<script lang="ts" setup>
import { AuthenticationForgetPassword } from '@vben/common-ui';

defineOptions({ name: 'ForgetPassword' });
</script>

<template>
  <AuthenticationForgetPassword
    :show-submit-button="false"
    sub-title="忘记密码时，请联系系统管理员重置密码。"
  />
</template>
