import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    { path: '/', name: 'chat', component: () => import('@/views/ChatView.vue') },
    {
      path: '/appointments',
      name: 'appointments',
      component: () => import('@/views/AppointmentsView.vue'),
    },
    {
      path: '/admin/upload',
      name: 'admin-upload',
      component: () => import('@/views/AdminUploadView.vue'),
      meta: { admin: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) {
    if (to.name === 'login' && auth.isAuthed) return { name: 'chat' }
    return true
  }
  if (!auth.isAuthed) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.admin && !auth.isAdmin) return { name: 'chat' }
  return true
})

export { router }
