import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    name: 'Profile',
    path: '/profile',
    component: () => import('#/views/_core/profile/index.vue'),
    meta: {
      authority: ['admin'],
      hideInMenu: true,
      icon: 'lucide:user',
      title: '个人中心',
    },
  },
];

export default routes;
