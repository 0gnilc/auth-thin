import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { AdminApi } from '#/api/system';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: '用户名',
    },
    {
      component: 'Input',
      fieldName: 'nickname',
      label: '昵称',
    },
    {
      component: 'Select',
      componentProps: {
        clearable: true,
        options: [
          { label: '启用', value: true },
          { label: '禁用', value: false },
        ],
      },
      fieldName: 'status',
      label: '启用状态',
    },
  ];
}

export function useColumns(
  onStatusChange?: (
    newStatus: boolean,
    row: AdminApi.Admin,
  ) => PromiseLike<boolean | undefined>,
): VxeTableGridColumns<AdminApi.Admin> {
  return [
    {
      field: 'username',
      minWidth: 150,
      title: '用户名',
    },
    {
      field: 'nickname',
      minWidth: 150,
      title: '昵称',
    },
    {
      field: 'roleCodes',
      minWidth: 220,
      showOverflow: false,
      slots: { default: 'roles' },
      title: '角色',
    },
    {
      align: 'center',
      cellRender: {
        attrs: { beforeChange: onStatusChange },
        name: onStatusChange ? 'CellSwitch' : 'CellTag',
      },
      field: 'status',
      title: '状态',
      width: 100,
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
      width: 200,
    },
  ];
}
