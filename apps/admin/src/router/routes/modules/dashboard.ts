import type { RouteRecordRaw } from 'vue-router';

const workspaceRedirectRoute: RouteRecordRaw = {
  meta: {
    authority: ['admin'],
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '首页',
  },
  name: 'WorkspaceRedirect',
  path: '/workspace',
  redirect: '/dashboard',
};

const routes: RouteRecordRaw[] = [
  {
    component: () => import('#/views/dashboard/index.vue'),
    meta: {
      affixTab: true,
      authority: ['admin'],
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: '首页',
    },
    name: 'Dashboard',
    path: '/dashboard',
  },
];

export { workspaceRedirectRoute };
export default routes;
