<script setup lang="ts">
import type { MenuForm, MenuType } from '../data';

import type { VbenFormSchema } from '#/adapter/form';
import type { MenuApi } from '#/api/system';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { isEqual, trimToNull } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { confirmDiscardChanges } from '#/adapter/confirm-discard-changes';
import { useVbenForm } from '#/adapter/form';
import { createMenu, MenuApi as MenuConstants, updateMenu } from '#/api/system';

import { createMenuForm, menuTypeLabels } from '../data';

/** 菜单创建或更新抽屉的菜单树及定位信息。 */
export interface MenuFormDrawerData {
  /** 当前可选父节点的菜单树数据。 */
  menus: MenuApi.Menu[];
  /** 新建菜单默认父节点 ID；省略时使用根节点。 */
  parentId?: string;
  /** 待编辑的菜单；省略时创建新菜单。 */
  row?: MenuApi.Menu;
}

/** 菜单父节点选择树，用于禁止选择自身及其后代。 */
interface ParentOption {
  /** 子级父节点选项；没有子项时省略。 */
  children?: ParentOption[];
  /** 是否禁止选为父节点；省略表示可选。 */
  disabled?: boolean;
  /** 父节点显示名称。 */
  label: string;
  /** 父菜单 ID，根节点使用字符串 0。 */
  value: string;
}

const emit = defineEmits<{ success: [] }>();

const menus = ref<MenuApi.Menu[]>([]);
const initialValues = ref<MenuForm>();
const saved = ref(false);

function flatten(items: MenuApi.Menu[], result: MenuApi.Menu[] = []) {
  for (const item of items) {
    result.push(item);
    flatten(item.children ?? [], result);
  }
  return result;
}

function findMenu(id: string | undefined) {
  return flatten(menus.value).find((item) => item.id === id);
}

/** 禁用当前节点及其后代作为父级，避免页面构造菜单环；最终树约束由 Server 校验。 */
function collectBlocked(id: string | undefined) {
  const blocked = new Set<string>();
  const visit = (items: MenuApi.Menu[], inside = false) => {
    for (const item of items) {
      const current = inside || item.id === id;
      if (current) blocked.add(item.id);
      visit(item.children ?? [], current);
    }
  };
  visit(menus.value);
  return blocked;
}

function parentAllows(parent: MenuApi.Menu, childType: MenuType) {
  return (
    parent.type === 'catalog' ||
    (parent.type === 'menu' && childType === 'button')
  );
}

function parentOptions(values: Partial<MenuForm>): ParentOption[] {
  const blocked = collectBlocked(values.id);
  const childType = values.type ?? 'menu';
  const map = (items: MenuApi.Menu[]): ParentOption[] =>
    items
      .filter(
        (item) =>
          (item.type === 'catalog' || item.type === 'menu') &&
          parentAllows(item, childType),
      )
      .map((item) => ({
        children: map(item.children ?? []),
        disabled: blocked.has(item.id),
        label: item.title,
        value: item.id,
      }));
  const options = map(menus.value);
  return childType === 'button'
    ? options
    : [
        {
          children: options,
          label: '根菜单',
          value: '0',
        },
      ];
}

const navigationTypes = new Set<MenuType>([
  'catalog',
  'embedded',
  'link',
  'menu',
]);
const pageTypes = new Set<MenuType>(['embedded', 'link', 'menu']);

/** 根据菜单种类决定必填的导航目标字段名。 */
type RequiredMenuField =
  | 'accessCode'
  | 'component'
  | 'iframeSrc'
  | 'link'
  | 'path';

const menuValidationMessages = {
  accessCode: '请输入按钮权限码',
  component: '请输入组件路径',
  iframeSrc: '请输入内嵌页面 URL',
  link: '请输入外链 URL',
  path: '请输入路由路径',
} as const satisfies Record<RequiredMenuField, string>;

const schema: VbenFormSchema[] = [
  { component: 'Input', fieldName: 'id', hide: true },
  {
    component: 'RadioGroup',
    dependencies: {
      resolve: ({ actions, values }) => ({
        componentProps: {
          class: 'flex flex-wrap',
          disabled: !!values.id,
          isButton: true,
          onChange: () => {
            void actions.setFieldValue('pid', undefined, false);
          },
          options: MenuConstants.MenuTypes.map((value) => ({
            label: menuTypeLabels[value],
            value,
          })),
        },
      }),
      triggerFields: ['id'],
    },
    fieldName: 'type',
    formItemClass: 'col-span-full',
    help: '保存成功后类型不可再修改',
    label: '菜单类型',
    rules: 'selectRequired',
  },
  {
    component: 'TreeSelect',
    dependencies: {
      resolve: ({ values }) => ({
        componentProps: {
          checkStrictly: true,
          data: parentOptions(values),
          defaultExpandAll: true,
          filterable: true,
          nodeKey: 'value',
          props: { children: 'children', disabled: 'disabled', label: 'label' },
        },
      }),
      triggerFields: ['id', 'type'],
    },
    fieldName: 'pid',
    label: '上级菜单',
    rules: 'selectRequired',
  },
  {
    component: 'Input',
    fieldName: 'name',
    label: '菜单名称',
    rules: 'required',
  },
  {
    component: 'Input',
    fieldName: 'title',
    label: '菜单标题',
    rules: 'required',
  },
  {
    component: 'Input',
    dependencies: {
      if: (values) => values.type !== 'button',
      triggerFields: ['type'],
    },
    fieldName: 'path',
    label: '路由路径',
  },
  {
    component: 'Input',
    dependencies: {
      if: (values) => values.type === 'menu',
      triggerFields: ['type'],
    },
    fieldName: 'component',
    label: '组件路径',
  },
  {
    component: 'Input',
    dependencies: {
      if: (values) => values.type === 'button',
      triggerFields: ['type'],
    },
    fieldName: 'accessCode',
    label: '按钮权限码',
  },
  {
    component: 'Input',
    componentProps: { maxlength: 500 },
    dependencies: {
      if: (values) => values.type === 'embedded',
      triggerFields: ['type'],
    },
    fieldName: 'iframeSrc',
    label: '内嵌页面 URL',
  },
  {
    component: 'Input',
    componentProps: { maxlength: 500 },
    dependencies: {
      if: (values) => values.type === 'link',
      triggerFields: ['type'],
    },
    fieldName: 'link',
    label: '外链 URL',
  },
  {
    component: 'RadioGroup',
    componentProps: {
      isButton: true,
      options: [
        { label: '启用', value: true },
        { label: '禁用', value: false },
      ],
    },
    fieldName: 'status',
    label: '启用状态',
  },
  {
    component: 'InputNumber',
    componentProps: { max: 9999, min: -9999 },
    fieldName: 'order',
    label: '排序',
  },
  {
    component: 'Divider',
    componentProps: { class: '!my-2' },
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'routingDivider',
    formItemClass: 'col-span-full !pb-2',
    hideLabel: true,
    renderComponentContent: () => ({
      default: () => '路由配置',
    }),
  },
  {
    component: 'Input',
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'redirect',
    label: '重定向',
  },
  {
    component: 'Input',
    dependencies: {
      if: (values) => pageTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'activePath',
    label: '激活菜单路径',
  },
  {
    component: 'IconPicker',
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'icon',
    label: '图标',
  },
  {
    component: 'Input',
    componentProps: {
      placeholder: JSON.stringify({ tab: 'overview' }),
      rows: 3,
      type: 'textarea',
    },
    dependencies: {
      if: (values) => pageTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'query',
    formItemClass: 'col-span-full',
    label: '路由查询参数（JSON 对象）',
  },
  {
    component: 'Divider',
    componentProps: { class: '!my-2' },
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'badgeDivider',
    formItemClass: 'col-span-full !pb-2',
    hideLabel: true,
    renderComponentContent: () => ({
      default: () => '徽标与标签页',
    }),
  },
  {
    component: 'Input',
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'badge',
    label: '徽标内容',
  },
  {
    component: 'Select',
    componentProps: {
      clearable: true,
      options: MenuConstants.BadgeTypes.map((value) => ({
        label: value,
        value,
      })),
    },
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'badgeType',
    label: '徽标类型',
  },
  {
    component: 'Select',
    componentProps: {
      clearable: true,
      options: MenuConstants.BadgeVariants.map((value) => ({
        label: value,
        value,
      })),
    },
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'badgeVariants',
    label: '徽标样式',
  },
  {
    component: 'InputNumber',
    componentProps: { min: 0 },
    dependencies: {
      if: (values) => values.type === 'menu' && values.affixTab,
      triggerFields: ['affixTab', 'type'],
    },
    fieldName: 'affixTabOrder',
    label: '固定标签页排序',
  },
  {
    component: 'InputNumber',
    componentProps: { min: 0 },
    dependencies: {
      if: (values) => values.type === 'menu',
      triggerFields: ['type'],
    },
    fieldName: 'maxNumOfOpenTab',
    label: '最大同名标签数',
  },
  {
    component: 'Divider',
    componentProps: { class: '!my-2' },
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'behaviorDivider',
    formItemClass: 'col-span-full !pb-2',
    hideLabel: true,
    renderComponentContent: () => ({
      default: () => '显示与行为',
    }),
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => values.type === 'menu',
      triggerFields: ['type'],
    },
    fieldName: 'affixTab',
    label: '固定标签页',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => pageTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'fullPathKey',
    label: '完整路径作为标签 Key',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => values.type === 'catalog' || values.type === 'menu',
      triggerFields: ['type'],
    },
    fieldName: 'hideChildrenInMenu',
    label: '隐藏子菜单',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'hideInBreadcrumb',
    label: '在面包屑隐藏',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => navigationTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'hideInMenu',
    label: '在菜单隐藏',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => pageTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'hideInTab',
    label: '在标签页隐藏',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => values.type === 'menu',
      triggerFields: ['type'],
    },
    fieldName: 'keepAlive',
    label: '缓存页面',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => pageTypes.has(values.type),
      triggerFields: ['type'],
    },
    fieldName: 'noBasicLayout',
    label: '不使用基础布局',
  },
  {
    component: 'Switch',
    dependencies: {
      if: (values) => values.type === 'link',
      triggerFields: ['type'],
    },
    fieldName: 'openInNewWindow',
    label: '在新窗口打开',
  },
];

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' } },
  schema,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1 sm:grid-cols-2',
});

function requiredByType(values: MenuForm) {
  const fields: Partial<Record<MenuType, RequiredMenuField[]>> = {
    button: ['accessCode'],
    catalog: ['path'],
    embedded: ['iframeSrc', 'path'],
    link: ['link', 'path'],
    menu: ['component', 'path'],
  };
  return fields[values.type]?.find(
    (field) => !String(values[field] ?? '').trim(),
  );
}

function isHttpUrl(value: unknown) {
  try {
    const url = new URL(String(value));
    return ['http:', 'https:'].includes(url.protocol) && !!url.hostname;
  } catch {
    return false;
  }
}

function validateMenu(values: MenuForm) {
  const missing = requiredByType(values);
  if (missing) {
    ElMessage.error(menuValidationMessages[missing]);
    return false;
  }
  if (
    (values.type === 'embedded' && !isHttpUrl(values.iframeSrc)) ||
    (values.type === 'link' && !isHttpUrl(values.link))
  ) {
    ElMessage.error('请输入完整的 http 或 https 地址');
    return false;
  }
  const parent = findMenu(values.pid);
  if (
    (values.pid === '0' && values.type === 'button') ||
    (parent && !parentAllows(parent, values.type))
  ) {
    ElMessage.error('所选上级菜单不能包含当前菜单类型');
    return false;
  }
  if (values.query) {
    try {
      const parsed = JSON.parse(values.query);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        throw new Error('Query must be an object');
      }
    } catch {
      ElMessage.error('路由查询参数必须是有效的 JSON 对象');
      return false;
    }
  }
  return true;
}

/** 菜单类型切换后显式清空已失效的字段，避免隐藏控件的旧值继续作为路由或权限配置提交。 */
function clearFieldsForType(values: MenuForm) {
  if (values.type !== 'button') values.accessCode = null;
  if (values.type !== 'menu') values.component = null;
  if (values.type !== 'embedded') values.iframeSrc = null;
  if (values.type !== 'link') values.link = null;
  if (values.type === 'button') {
    values.path = null;
    values.redirect = null;
    values.activePath = null;
    values.icon = null;
    values.query = null;
    values.badge = null;
    values.badgeType = null;
    values.badgeVariants = null;
  }
}

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
    const values = await formApi.getValues<MenuForm>();
    trimToNull(values);
    if (!validateMenu(values)) return;
    clearFieldsForType(values);
    const {
      badgeDivider: _badgeDivider,
      behaviorDivider: _behaviorDivider,
      id,
      routingDivider: _routingDivider,
      ...data
    } = values as MenuForm & Record<string, unknown>;
    drawerApi.lock();
    try {
      await (id ? updateMenu({ id, ...data }) : createMenu(data));
      saved.value = true;
      ElMessage.success('菜单已保存');
      emit('success');
      await drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    saved.value = false;
    const payload = drawerApi.getData<MenuFormDrawerData>();
    menus.value = payload.menus;
    const row = payload.row;
    const parent = findMenu(payload.parentId);
    const values: MenuForm = row
      ? {
          accessCode: row.accessCode ?? null,
          activePath: row.activePath ?? null,
          affixTab: row.affixTab,
          affixTabOrder: row.affixTabOrder ?? null,
          badge: row.badge ?? null,
          badgeType: row.badgeType ?? null,
          badgeVariants: row.badgeVariants ?? null,
          component: row.component ?? null,
          fullPathKey: row.fullPathKey,
          hideChildrenInMenu: row.hideChildrenInMenu,
          hideInBreadcrumb: row.hideInBreadcrumb,
          hideInMenu: row.hideInMenu,
          hideInTab: row.hideInTab,
          icon: row.icon ?? null,
          id: row.id,
          iframeSrc: row.iframeSrc ?? null,
          keepAlive: row.keepAlive,
          link: row.link ?? null,
          maxNumOfOpenTab: row.maxNumOfOpenTab ?? null,
          name: row.name,
          noBasicLayout: row.noBasicLayout,
          openInNewWindow: row.openInNewWindow,
          order: row.order,
          path: row.path ?? null,
          pid: row.pid,
          query: row.query ?? null,
          redirect: row.redirect ?? null,
          status: row.status,
          title: row.title,
          type: row.type,
        }
      : createMenuForm(
          payload.parentId ?? '0',
          parent?.type === 'menu' ? 'button' : 'menu',
        );
    drawerApi.setState({
      title: row ? '修改菜单' : '新增菜单',
    });
    await formApi.reset();
    await nextTick();
    await formApi.setValues(values, false);
    initialValues.value = await formApi.getValues<MenuForm>();
  },
});
</script>

<template>
  <Drawer class="w-full sm:max-w-4xl" content-class="overflow-y-auto">
    <Form class="w-full" />
  </Drawer>
</template>
