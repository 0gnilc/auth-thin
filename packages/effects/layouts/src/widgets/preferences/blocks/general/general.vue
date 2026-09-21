<script setup lang="ts">
import { onMounted, ref, unref } from 'vue';

import { useTimezoneStore } from '@vben/stores';

import InputItem from '../input-item.vue';
import SelectItem from '../select-item.vue';
import SwitchItem from '../switch-item.vue';

defineOptions({
  name: 'PreferenceGeneralConfig',
});

const appTimezone = defineModel<string>('appTimezone');
const appDynamicTitle = defineModel<boolean>('appDynamicTitle');
const appWatermark = defineModel<boolean>('appWatermark');
const appWatermarkContent = defineModel<string>('appWatermarkContent');
const appEnableCheckUpdates = defineModel<boolean>('appEnableCheckUpdates');
const appEnableCopyPreferences = defineModel<boolean>(
  'appEnableCopyPreferences',
);
const timezoneStore = useTimezoneStore();

const timezoneOptionsRef = ref<
  {
    label: string;
    value: string;
  }[]
>([]);

onMounted(async () => {
  timezoneOptionsRef.value = await timezoneStore.getTimezoneOptions();
  // 获取当前时区，例如：Asia/Shanghai
  const timezoneValue = unref(timezoneStore.timezone);
  if (timezoneValue) {
    appTimezone.value = timezoneValue;
  }
});

async function onTimezoneChange(timezone: string | undefined) {
  if (timezone) await timezoneStore.setTimezone(timezone);
}
</script>

<template>
  <SelectItem
    v-model="appTimezone"
    :items="timezoneOptionsRef"
    @update:model-value="onTimezoneChange"
  >
    时区
  </SelectItem>
  <SwitchItem v-model="appDynamicTitle"> 动态标题 </SwitchItem>
  <SwitchItem
    v-model="appWatermark"
    @update:model-value="
      (val) => {
        if (!val) appWatermarkContent = '';
      }
    "
  >
    水印
  </SwitchItem>
  <InputItem
    v-if="appWatermark"
    v-model="appWatermarkContent"
    placeholder="请输入水印文案"
  >
    请输入水印文案
  </InputItem>
  <SwitchItem v-model="appEnableCheckUpdates"> 定时检查更新 </SwitchItem>
  <SwitchItem v-model="appEnableCopyPreferences">
    显示复制偏好设置按钮
  </SwitchItem>
</template>
