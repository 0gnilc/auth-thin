/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions, RequestErrorType } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import { useAuthStore } from '#/store';

import { refresh } from './core';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

const requestErrorMessages: Record<RequestErrorType, string> = {
  'bad-request': '请求错误。请检查您的输入并重试。',
  forbidden: '禁止访问, 您没有权限访问此资源。',
  'internal-server-error': '内部服务器错误，请稍后再试。',
  'network-error': '网络异常，请检查您的网络连接后重试。',
  'not-found': '未找到, 请求的资源不存在。',
  'request-timeout': '请求超时，请稍后再试。',
  unauthorized: '登录认证过期，请重新登录后继续。',
};

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });

  /**
   * 重新认证逻辑
   */
  async function doReAuthenticate() {
    console.warn('Access token or refresh token is invalid or expired. ');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    accessStore.setAccessToken(null);
    accessStore.setRefreshToken(null);
    if (
      preferences.app.loginExpiredMode === 'modal' &&
      accessStore.isAccessChecked
    ) {
      accessStore.setLoginExpired(true);
    } else {
      await authStore.logout();
    }
  }

  /**
   * 刷新token逻辑
   */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const currentRefreshToken = accessStore.refreshToken;
    if (!currentRefreshToken) {
      throw new Error('Refresh token is missing.');
    }

    const session = await refresh(currentRefreshToken);
    accessStore.setAccessToken(session.accessToken);
    accessStore.setRefreshToken(session.refreshToken);
    return session.accessToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      return config;
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor({
      onError: (message, error: any) => {
        const responseData = error?.response?.data ?? {};
        const responseMessage =
          responseData?.error ?? responseData?.message ?? '';
        ElMessage.error(responseMessage || message);
      },
      resolveMessage: (type) => requestErrorMessages[type],
    }),
  );

  return client;
}

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });
