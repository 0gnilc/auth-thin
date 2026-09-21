import { beforeEach, describe, expect, it, vi } from 'vitest';

import { confirmDiscardChanges } from './confirm-discard-changes';

const { confirm } = vi.hoisted(() => ({
  confirm: vi.fn(),
}));

vi.mock('element-plus', () => ({
  ElMessageBox: { confirm },
}));

describe('confirmDiscardChanges', () => {
  beforeEach(() => {
    confirm.mockReset();
  });

  it('allows closing without asking when nothing changed', async () => {
    await expect(confirmDiscardChanges(false)).resolves.toBe(true);
    expect(confirm).not.toHaveBeenCalled();
  });

  it('allows closing after discarding changes', async () => {
    confirm.mockResolvedValue(undefined);

    await expect(confirmDiscardChanges(true)).resolves.toBe(true);
    expect(confirm).toHaveBeenCalledOnce();
  });

  it.each(['cancel', 'close'])('blocks leaving after %s', async (action) => {
    confirm.mockRejectedValue(action);
    await expect(confirmDiscardChanges(true)).resolves.toBe(false);
    expect(confirm).toHaveBeenCalledOnce();
  });

  it.each([
    [undefined, '放弃修改', '继续编辑'],
    [{}, '放弃修改', '继续编辑'],
    [{ confirmButtonText: 'Leave' }, 'Leave', '继续编辑'],
    [{ cancelButtonText: 'Stay' }, '放弃修改', 'Stay'],
    [{ confirmButtonText: 'Leave', cancelButtonText: 'Stay' }, 'Leave', 'Stay'],
  ])(
    'uses independent button defaults for %j',
    async (options, confirmButtonText, cancelButtonText) => {
      confirm.mockResolvedValue(undefined);
      await expect(confirmDiscardChanges(true, options)).resolves.toBe(true);
      expect(confirm).toHaveBeenCalledWith(
        '当前内容尚未保存，确定放弃修改吗？',
        '未保存的修改',
        { confirmButtonText, cancelButtonText, type: 'warning' },
      );
    },
  );
});
