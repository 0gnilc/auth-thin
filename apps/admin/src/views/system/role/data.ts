import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { RoleApi } from '#/api/system';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'code',
      label: '角色标识',
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '角色名称',
    },
    {
      component: 'Select',
      componentProps: {
        clearable: true,
        options: [
          { label: '内置', value: true },
          { label: '自定义', value: false },
        ],
      },
      fieldName: 'builtIn',
      label: '类型',
    },
  ];
}

export function useColumns(): VxeTableGridColumns<RoleApi.Role> {
  return [
    {
      field: 'code',
      minWidth: 170,
      title: '角色标识',
    },
    {
      field: 'name',
      minWidth: 170,
      title: '角色名称',
    },
    {
      field: 'remark',
      minWidth: 220,
      title: '描述',
    },
    {
      align: 'center',
      field: 'builtIn',
      slots: { default: 'type' },
      title: '类型',
      width: 110,
    },
    {
      field: 'createTime',
      formatter: 'formatDateTime',
      title: '创建时间',
      width: 180,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: '操作',
      width: 280,
    },
  ];
}
