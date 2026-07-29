import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginPage.vue'),
    meta: { layout: 'auth' },
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomePage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/learn',
    name: 'LearnPage',
    component: () => import('@/views/LearnPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/vocabulary',
    name: 'Vocabulary',
    component: () => import('@/views/VocabularyPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/grammar',
    name: 'GrammarCenter',
    component: () => import('@/views/GrammarCenter.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/reading',
    name: 'ReadingPractice',
    component: () => import('@/views/ReadingPractice.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/writing',
    name: 'WritingPractice',
    component: () => import('@/views/WritingPractice.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/FavoritesPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/progress',
    name: 'Progress',
    component: () => import('@/views/ProgressPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/practice',
    name: 'DailyPractice',
    component: () => import('@/views/DailyPractice.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/stats',
    name: 'StatsDashboard',
    component: () => import('@/views/StatsDashboard.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfilePage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/flashcards',
    name: 'Flashcards',
    component: () => import('@/views/FlashcardsPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/HistoryPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminPage.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/linkage',
    name: 'LinkageCenter',
    component: () => import('@/views/LinkageCenter.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/ai',
    name: 'AIAssistant',
    component: () => import('@/views/AIAssistant.vue'),
    meta: { layout: 'main', requiresAuth: true },
  },
  {
    path: '/grammar-practice',
    name: 'GrammarPractice',
    component: () => import('@/views/GrammarPractice.vue'),
    meta: { title: '语法练习', requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局导航守卫：未登录自动跳转到登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录用户访问登录页 → 直接进首页
    next('/')
  } else {
    next()
  }
})

export default router
