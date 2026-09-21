import { ElMessageBox } from 'element-plus';

export async function confirmDiscardChanges(
  hasUnsavedChanges: boolean,
  options?: {
    cancelButtonText?: string;
    confirmButtonText?: string;
  },
): Promise<boolean> {
  if (!hasUnsavedChanges) return true;

  try {
    await ElMessageBox.confirm(
      '当前内容尚未保存，确定放弃修改吗？',
      '未保存的修改',
      {
        cancelButtonText: options?.cancelButtonText ?? '继续编辑',
        confirmButtonText: options?.confirmButtonText ?? '放弃修改',
        type: 'warning',
      },
    );
    return true;
  } catch {
    return false;
  }
}
