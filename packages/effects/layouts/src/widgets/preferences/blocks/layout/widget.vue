<script setup lang="ts">
import type { PreferencesButtonPositionType, SelectOption } from '@vben/types';

import { computed } from 'vue';

import DraggableList from '../draggable-list.vue';

defineOptions({
  name: 'PreferenceInterfaceControl',
});

const widgetOrder = defineModel<string[]>('widgetOrder', { required: true });

const widgetGlobalSearchButtonPosition = defineModel<string>(
  'widgetGlobalSearchButtonPosition',
);
const widgetFullscreenButtonPosition = defineModel<string>(
  'widgetFullscreenButtonPosition',
);
const widgetNotificationButtonPosition = defineModel<string>(
  'widgetNotificationButtonPosition',
);
const widgetThemeToggleButtonPosition = defineModel<string>(
  'widgetThemeToggleButtonPosition',
);
const widgetLockScreenButtonPosition = defineModel<string>(
  'widgetLockScreenButtonPosition',
);
const widgetLogoutButtonPosition = defineModel<string>(
  'widgetLogoutButtonPosition',
);
const widgetRefreshButtonPosition = defineModel<string>(
  'widgetRefreshButtonPosition',
);
const widgetTimezoneButtonPosition = defineModel<string>(
  'widgetTimezoneButtonPosition',
);
const appPreferencesButtonPosition = defineModel<PreferencesButtonPositionType>(
  'appPreferencesButtonPosition',
);

const buttonPositionItems = computed((): SelectOption[] => [
  {
    label: '顶栏',
    value: 'header',
  },
  {
    label: '用户下拉窗',
    value: 'user-dropdown',
  },
  {
    label: '不显示',
    value: 'none',
  },
]);

/**
 * preferences 按钮独享的位置选项：
 * 保留 auto/fixed 让 use-preferences.ts 的智能 fallback 生效
 * （移动端/全屏内容模式下自动切换 header/fixed，避免偏好入口死锁）
 */
const preferencesPositionItems = computed((): SelectOption[] => [
  {
    label: '自动',
    value: 'auto',
  },
  {
    label: '顶栏',
    value: 'header',
  },
  {
    label: '固定',
    value: 'fixed',
  },
  {
    label: '用户下拉窗',
    value: 'user-dropdown',
  },
  {
    label: '不显示',
    value: 'none',
  },
]);

const positionMap: Record<string, string> = {
  globalSearch: 'widgetGlobalSearchButtonPosition',
  preferences: 'appPreferencesButtonPosition',
  themeToggle: 'widgetThemeToggleButtonPosition',
  timezone: 'widgetTimezoneButtonPosition',
  fullscreen: 'widgetFullscreenButtonPosition',
  refresh: 'widgetRefreshButtonPosition',
  notification: 'widgetNotificationButtonPosition',
  lockScreenBtn: 'widgetLockScreenButtonPosition',
  logoutBtn: 'widgetLogoutButtonPosition',
};

const labelMap: Record<string, string> = {
  globalSearch: '全局搜索',
  preferences: '偏好设置',
  themeToggle: '主题切换',
  timezone: '时区',
  fullscreen: '全屏',
  refresh: '刷新',
  notification: '通知',
  lockScreenBtn: '锁定屏幕',
  logoutBtn: '退出登录',
};

const draggableItems = computed(() =>
  (widgetOrder.value ?? []).map((key) => ({
    key,
    label: labelMap[key] ?? key,
    position: getPosition(key),
    positionItems:
      key === 'preferences' ? preferencesPositionItems.value : undefined,
  })),
);

function getPosition(
  key: string,
): 'auto' | 'fixed' | 'header' | 'none' | 'user-dropdown' {
  const modelName = positionMap[key];
  if (!modelName) return 'none';
  const modelMap: Record<string, any> = {
    widgetGlobalSearchButtonPosition,
    widgetFullscreenButtonPosition,
    widgetNotificationButtonPosition,
    widgetThemeToggleButtonPosition,
    widgetLockScreenButtonPosition,
    widgetLogoutButtonPosition,
    widgetRefreshButtonPosition,
    widgetTimezoneButtonPosition,
    appPreferencesButtonPosition,
  };
  return modelMap[modelName]?.value ?? 'none';
}

function handleUpdateOrder(keys: string[]) {
  widgetOrder.value = keys;
}

function handleUpdatePosition(
  key: string,
  position: 'auto' | 'fixed' | 'header' | 'none' | 'user-dropdown',
) {
  const modelName = positionMap[key];
  if (!modelName) return;
  const modelMap: Record<string, any> = {
    widgetGlobalSearchButtonPosition,
    widgetFullscreenButtonPosition,
    widgetNotificationButtonPosition,
    widgetThemeToggleButtonPosition,
    widgetLockScreenButtonPosition,
    widgetLogoutButtonPosition,
    widgetRefreshButtonPosition,
    widgetTimezoneButtonPosition,
    appPreferencesButtonPosition,
  };
  modelMap[modelName].value = position;
}
</script>

<template>
  <DraggableList
    :items="draggableItems"
    :position-items="buttonPositionItems"
    @update-order="handleUpdateOrder"
    @update-position="handleUpdatePosition"
  />
</template>
