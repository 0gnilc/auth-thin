import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { PermissionApi } from '#/api/system';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'code',
      label: '权限标识',
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '权限名称',
    },
    {
      component: 'Input',
      fieldName: 'targetIdentifier',
      label: '访问目标标识',
    },
    {
      component: 'Select',
      componentProps: {
        clearable: true,
        options: [
          { label: '公开访问', value: true },
          { label: '需要授权', value: false },
        ],
      },
      fieldName: 'publicAccess',
      label: '访问方式',
    },
  ];
}

export function useColumns(): VxeTableGridColumns<PermissionApi.Permission> {
  return [
    {
      field: 'code',
      minWidth: 210,
      title: '权限标识',
    },
    {
      field: 'name',
      minWidth: 170,
      title: '权限名称',
    },
    {
      field: 'targetQualifier',
      title: '限定符',
      width: 100,
    },
    {
      field: 'targetIdentifier',
      minWidth: 240,
      title: '访问目标',
    },
    {
      align: 'center',
      field: 'publicAccess',
      slots: { default: 'access' },
      title: '访问方式',
      width: 120,
    },
    {
      align: 'center',
      field: 'builtIn',
      slots: { default: 'type' },
      title: '类型',
      width: 110,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: '操作',
      width: 120,
    },
  ];
}
