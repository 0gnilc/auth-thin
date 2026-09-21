import type { Page } from 'playwright/test';

import { expect, test } from 'playwright/test';

interface ApiEnvelope<T = unknown> {
  code: number;
  data: T;
}

/** 读取真实测试 Server 的统一业务响应；类型断言只简化夹具访问，业务 code 仍由各场景断言。 */
async function readApiResponse<T = unknown>(response: {
  json: () => Promise<unknown>;
}) {
  const body = await response.json();
  return body as ApiEnvelope<T>;
}

async function passSliderCaptcha(page: Page) {
  const action = page.locator('[name="captcha-action"]');
  const wrapper = action.locator('..');
  await expect(action).toBeVisible();
  await expect
    .poll(async () => {
      const currentActionBox = await action.boundingBox();
      const currentWrapperBox = await wrapper.boundingBox();
      if (!currentActionBox || !currentWrapperBox) {
        return Number.POSITIVE_INFINITY;
      }
      return Math.abs(currentActionBox.x - currentWrapperBox.x);
    })
    .toBeLessThanOrEqual(2);
  const actionBox = await action.boundingBox();
  const wrapperBox = await wrapper.boundingBox();
  if (!actionBox || !wrapperBox) throw new Error('Captcha is not measurable');

  await page.mouse.move(
    actionBox.x + actionBox.width / 2,
    actionBox.y + actionBox.height / 2,
  );
  await page.mouse.down();
  await page.mouse.move(
    wrapperBox.x + wrapperBox.width - actionBox.width / 2 - 2,
    actionBox.y + actionBox.height / 2,
    { steps: 8 },
  );
  await page.mouse.up();
  await expect(wrapper).toContainText(/Passed|验证通过/);
}

/** 先监听响应再点击，避免快速返回的登录请求在监听建立前完成。 */
async function submitLogin(page: Page) {
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/sys/admin/login'),
  );
  await page.getByRole('button', { name: 'login' }).click();
  return responsePromise;
}

async function loginSuccessfully(
  page: Page,
  usernameValue: string,
  passwordValue: string,
) {
  await page.goto('/auth/login');
  await page
    .locator('input:not([type="checkbox"])')
    .first()
    .fill(usernameValue);
  await page.locator('input[type="password"]').first().fill(passwordValue);
  await passSliderCaptcha(page);
  const response = await submitLogin(page);
  const body = await readApiResponse<{
    accessToken: string;
    refreshToken: string;
  }>(response);
  expect(body.code).toBe(0);
  await expect(page).toHaveURL(/\/dashboard/);
  return body.data;
}

function rowContaining(page: Page, text: string) {
  return page.locator('.vxe-body--row').filter({ hasText: text });
}

test('failed login can be retried and the resulting session can log out', async ({
  page,
}) => {
  await page.goto('/auth/login');
  const username = page.locator('input:not([type="checkbox"])').first();
  const password = page.locator('input[type="password"]').first();
  await username.fill('admin');
  await password.fill('incorrect-password');
  await passSliderCaptcha(page);

  const failedResponse = await submitLogin(page);
  const failedBody = await readApiResponse(failedResponse);
  expect(failedBody.code).not.toBe(0);
  await expect(page).toHaveURL(/\/auth\/login/);

  await password.fill('123456');
  await passSliderCaptcha(page);
  const successResponse = await submitLogin(page);
  const successBody = await readApiResponse(successResponse);
  expect(successBody.code).toBe(0);
  await expect(page).toHaveURL(/\/dashboard/);

  await page.getByRole('banner').getByRole('button').last().click();
  await page.getByRole('menuitem', { name: /Logout|退出登录/ }).click();
  await page.getByRole('button', { name: /^(Confirm|确认)$/ }).click();
  await expect(page).toHaveURL(/\/auth\/login/);
});

test('administrator CRUD preserves unsaved edits until the operator decides', async ({
  page,
}) => {
  const usernameValue = `e2e-admin-${Date.now()}`;
  await loginSuccessfully(page, 'admin', '123456');
  await page.goto('/system/admin');
  await page
    .getByRole('button', { name: /Add administrator|新增后台管理员/ })
    .click();

  let drawer = page.getByRole('dialog').filter({
    hasText: /Add administrator|新增后台管理员/,
  });
  await drawer.getByLabel(/Username|用户名/).fill(usernameValue);
  await drawer.getByLabel(/Password|密码/).fill('Strong#123');
  await drawer.getByLabel(/Nickname|昵称/).fill('E2E Original');
  const createResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/sys/admin/create'),
  );
  await drawer.getByRole('button', { name: /Confirm|确认/ }).click();
  const createResponse = await createResponsePromise;
  const createBody = await readApiResponse(createResponse);
  expect(createBody.code).toBe(0);
  await expect(drawer).toBeHidden();

  let row = rowContaining(page, usernameValue);
  await expect(row).toContainText('E2E Original');
  await row.getByRole('button', { name: /Edit|修改/ }).click();
  drawer = page.getByRole('dialog').filter({
    hasText: /Edit administrator|修改后台管理员/,
  });
  const nickname = drawer.getByLabel(/Nickname|昵称/);
  await nickname.fill('E2E Unsaved');
  await drawer.getByRole('button').first().click();
  const unsavedDialog = page.getByRole('dialog').filter({
    hasText: /Unsaved changes|未保存的修改/,
  });
  await unsavedDialog
    .getByRole('button', { name: /Keep editing|继续编辑/ })
    .click();
  await expect(nickname).toHaveValue('E2E Unsaved');

  await drawer.getByRole('button').first().click();
  await page
    .getByRole('dialog')
    .filter({ hasText: /Unsaved changes|未保存的修改/ })
    .getByRole('button', { name: /Discard changes|放弃修改/ })
    .click();
  await expect(drawer).toBeHidden();

  row = rowContaining(page, usernameValue);
  await row.getByRole('button', { name: /Edit|修改/ }).click();
  drawer = page.getByRole('dialog').filter({
    hasText: /Edit administrator|修改后台管理员/,
  });
  await drawer.getByLabel(/Nickname|昵称/).fill('E2E Saved');
  const updateResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/sys/admin/update'),
  );
  await drawer.getByRole('button', { name: /Confirm|确认/ }).click();
  const updateResponse = await updateResponsePromise;
  const updateBody = await readApiResponse(updateResponse);
  expect(updateBody.code).toBe(0);
  await expect(rowContaining(page, usernameValue)).toContainText('E2E Saved');

  row = rowContaining(page, usernameValue);
  await row.getByRole('button').last().click();
  await page.getByRole('menuitem', { name: /Remove|删除/ }).click();
  const removeResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/sys/admin/remove/'),
  );
  await page
    .getByRole('button', { name: /Confirm|确认/ })
    .last()
    .click();
  const removeResponse = await removeResponsePromise;
  const removeBody = await readApiResponse(removeResponse);
  expect(removeBody.code).toBe(0);
  await expect(rowContaining(page, usernameValue)).toHaveCount(0);
});

// 同时检查菜单可见性和直接接口访问，证明前端隐藏不能代替服务端权限执行。
test('baseline administrators cannot see or call management capabilities', async ({
  page,
}) => {
  const limitedUsername = `e2e-limited-${Date.now()}`;
  const managerSession = await loginSuccessfully(page, 'admin', '123456');
  const managerHeaders = {
    Authorization: `Bearer ${managerSession.accessToken}`,
  };
  const createResponse = await page.request.post('/api/sys/admin/create', {
    data: {
      homePath: '/dashboard',
      nickname: 'E2E Limited',
      password: 'Strong#123',
      status: true,
      username: limitedUsername,
    },
    headers: managerHeaders,
  });
  const createBody = await readApiResponse(createResponse);
  expect(createBody.code).toBe(0);

  const limitedLogin = await page.request.post('/api/sys/admin/login', {
    data: { password: 'Strong#123', username: limitedUsername },
  });
  const limitedLoginBody = await readApiResponse<{
    accessToken: string;
  }>(limitedLogin);
  const limitedSession = limitedLoginBody.data;
  const forbidden = await page.request.post('/api/sys/admin/page', {
    data: { currentPage: 1, pageSize: 10 },
    headers: { Authorization: `Bearer ${limitedSession.accessToken}` },
  });
  expect(forbidden.status()).toBe(403);

  await page.evaluate(() => localStorage.clear());
  await loginSuccessfully(page, limitedUsername, 'Strong#123');
  await expect(
    page.getByText(/System Management|系统管理/, { exact: true }),
  ).toHaveCount(0);
  await page.goto('/system/admin');
  await expect(
    page.getByRole('button', { name: /Add administrator|新增后台管理员/ }),
  ).toHaveCount(0);

  const pageResponse = await page.request.post('/api/sys/admin/page', {
    data: {
      currentPage: 1,
      pageSize: 10,
      username: limitedUsername,
    },
    headers: managerHeaders,
  });
  const pageBody = await readApiResponse<{ list: Array<{ id: string }> }>(
    pageResponse,
  );
  const limitedAdmin = pageBody.data.list[0];
  if (!limitedAdmin) throw new Error('Limited administrator was not found');
  const removeResponse = await page.request.post(
    `/api/sys/admin/remove/${limitedAdmin.id}`,
    { headers: managerHeaders },
  );
  const removeBody = await readApiResponse(removeResponse);
  expect(removeBody.code).toBe(0);
});

/** 菜单树接口中用于检查显示文本的节点。 */
interface MenuTitleRow {
  /** 菜单记录 ID。 */
  id: string;
  /** 唯一路由名称。 */
  name: string;
  /** 直接展示的菜单标题。 */
  title: string;
}

test('菜单标题直接保存文本，编辑后刷新仍显示原文', async ({ page }) => {
  const messageRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/sys/i18n-message/')) {
      messageRequests.push(request.url());
    }
  });
  const session = await loginSuccessfully(page, 'admin', '123456');
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const name = `StandardMenu${Date.now()}`;
  const title = `运营.日报-${name}`;
  const createResponse = await page.request.post('/api/authz/menu/create', {
    headers,
    data: {
      affixTab: false,
      component: 'BasicLayout',
      fullPathKey: true,
      hideChildrenInMenu: false,
      hideInBreadcrumb: false,
      hideInMenu: false,
      hideInTab: false,
      keepAlive: false,
      name,
      noBasicLayout: false,
      openInNewWindow: false,
      order: 999,
      path: `/standard-${name}`,
      pid: '0',
      status: true,
      title,
      type: 'catalog',
    },
  });
  const created = await readApiResponse(createResponse);
  expect(created.code).toBe(0);
  const treeResponse = await page.request.post('/api/authz/menu/tree', {
    headers,
  });
  const tree = await readApiResponse<MenuTitleRow[]>(treeResponse);
  const menu = tree.data.find((row) => row.name === name);
  if (!menu) throw new Error('新建菜单未出现在菜单树中');
  expect(menu.title).toBe(title);

  await page.goto('/system/menu');
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
  await page.getByRole('textbox', { name: '关键词' }).fill(name);
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  const row = rowContaining(page, name);
  await expect(row).toContainText(title);
  // VXE 的固定操作列单独渲染；筛选到唯一菜单后定位其可见操作。
  await expect(row).toHaveCount(1);
  await page.getByRole('button', { name: '修改', exact: true }).click();
  const drawer = page.getByRole('dialog').filter({ hasText: '修改菜单' });
  const input = drawer.getByLabel(/菜单标题/);
  await expect(input).toHaveValue(title);
  const updatedTitle = `${title}.已修改`;
  await input.fill(updatedTitle);
  const saved = page.waitForResponse((response) =>
    response.url().endsWith('/api/authz/menu/update'),
  );
  await drawer.getByRole('button', { name: '确认', exact: true }).click();
  const savedBody = await readApiResponse(await saved);
  expect(savedBody.code).toBe(0);
  await expect(drawer).toBeHidden();
  await page.reload();
  await page.getByRole('textbox', { name: '关键词' }).fill(name);
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(rowContaining(page, name)).toContainText(updatedTitle);
  expect(messageRequests).toEqual([]);
  const removed = await page.request.post(`/api/authz/menu/remove/${menu.id}`, {
    headers,
  });
  const removedBody = await readApiResponse(removed);
  expect(removedBody.code).toBe(0);
});
