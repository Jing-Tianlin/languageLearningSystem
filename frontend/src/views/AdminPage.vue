<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getExamLevels } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import { LANG_NAMES } from '@/config/languages'
import { toast } from '@/composables/useToast'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'

const authStore = useAuthStore()
const BASE = API_BASE_URL

const tab = ref('dashboard')
const loading = ref(false)

const dashboard = ref(null)

const users = ref([])
const totalUsers = ref(0)
const userPage = ref(1)
const userPageSize = 10
const userSearch = ref('')

const roles = ref([])

const logs = ref([])
const totalLogs = ref(0)
const logPage = ref(1)
const logPageSize = 20
const logSearch = ref('')

const showUserModal = ref(false)
const editingUser = ref(null)
const resetPwdUserId = ref(null)
const newPassword = ref('')
const showResetPwdModal = ref(false)

const isAdmin = computed(() => authStore.user?.roles?.includes('ROLE_ADMIN'))

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  if (authStore.token) {
    headers['Authorization'] = 'Bearer ' + authStore.token
  }
  return headers
}

onMounted(async () => {
  if (isAdmin.value) {
    await loadDashboard()
    await loadRoles()
  }
})

async function loadDashboard() {
  try {
    const res = await fetch(`${BASE}/admin/stats/dashboard`, {
      headers: authHeaders()
    })
    const json = await res.json()
    if (json.code === 200) {
      dashboard.value = json.data
    }
  } catch (e) {
    console.error('加载仪表盘失败', e)
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      pageNo: String(userPage.value),
      pageSize: String(userPageSize)
    })
    if (userSearch.value.trim()) params.set('keyword', userSearch.value.trim())
    
    const res = await fetch(`${BASE}/admin/users?${params}`, {
      headers: authHeaders()
    })
    const json = await res.json()
    if (json.code === 200) {
      users.value = json.data?.records || []
      totalUsers.value = json.data?.total || 0
    }
  } catch (e) {
    toast.error('加载用户失败')
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await fetch(`${BASE}/admin/roles`, {
      headers: authHeaders()
    })
    const json = await res.json()
    if (json.code === 200) {
      roles.value = json.data || []
    }
  } catch (e) {}
}

async function loadLogs() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      pageNo: String(logPage.value),
      pageSize: String(logPageSize)
    })
    if (logSearch.value.trim()) params.set('keyword', logSearch.value.trim())
    
    const res = await fetch(`${BASE}/admin/logs?${params}`, {
      headers: authHeaders()
    })
    const json = await res.json()
    if (json.code === 200) {
      logs.value = json.data?.records || []
      totalLogs.value = json.data?.total || 0
    } else {
      toast.error(json.message || '加载日志失败')
    }
  } catch (e) {
    toast.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

async function toggleRole(userId, roleId) {
  if (!isAdmin.value) { toast.warning('无权限'); return }
  try {
    await fetch(`${BASE}/admin/user-roles`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ userId, roleId }),
    })
    await loadUsers()
    toast.success('角色已更新')
  } catch (e) {
    toast.error('操作失败')
  }
}

function openCreateUser() {
  editingUser.value = {
    username: '',
    password: '',
    nickname: '',
    email: ''
  }
  showUserModal.value = true
}

function openEditUser(user) {
  editingUser.value = { ...user }
  showUserModal.value = true
}

async function saveUser() {
  if (!editingUser.value.username?.trim()) {
    toast.error('用户名不能为空')
    return
  }
  if (!editingUser.value.id && !editingUser.value.password) {
    toast.error('密码不能为空')
    return
  }
  
  try {
    const url = editingUser.value.id ? `${BASE}/admin/users` : `${BASE}/admin/users`
    const method = editingUser.value.id ? 'PUT' : 'POST'
    
    const res = await fetch(url, {
      method,
      headers: authHeaders(),
      body: JSON.stringify(editingUser.value),
    })
    const json = await res.json()
    if (json.code === 200) {
      toast.success(editingUser.value.id ? '更新成功' : '创建成功')
      showUserModal.value = false
      await loadUsers()
      await loadDashboard()
    } else {
      toast.error(json.message || '操作失败')
    }
  } catch (e) {
    toast.error('操作失败')
  }
}

function openResetPassword(userId) {
  resetPwdUserId.value = userId
  newPassword.value = ''
  showResetPwdModal.value = true
}

async function confirmResetPassword() {
  if (!newPassword.value || newPassword.value.length < 6) {
    toast.error('密码长度不能少于6位')
    return
  }
  try {
    const res = await fetch(`${BASE}/admin/users/${resetPwdUserId.value}/reset-password`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ password: newPassword.value }),
    })
    const json = await res.json()
    if (json.code === 200) {
      toast.success('密码重置成功')
      showResetPwdModal.value = false
    } else {
      toast.error(json.message || '重置失败')
    }
  } catch (e) {
    toast.error('重置失败')
  }
}

async function toggleUserStatus(user) {
  if (!confirm(`确定要${user.status === 1 ? '禁用' : '启用'}该用户吗？`)) return
  try {
    const res = await fetch(`${BASE}/admin/users/${user.id}/toggle-status`, {
      method: 'POST',
      headers: authHeaders(),
    })
    const json = await res.json()
    if (json.code === 200) {
      toast.success('操作成功')
      await loadUsers()
    } else {
      toast.error(json.message || '操作失败')
    }
  } catch (e) {
    toast.error('操作失败')
  }
}

async function deleteUser(userId) {
  if (!confirm('确定要删除该用户吗？此操作不可恢复。')) return
  try {
    const res = await fetch(`${BASE}/admin/users/${userId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    const json = await res.json()
    if (json.code === 200) {
      toast.success('用户已删除')
      await loadUsers()
      await loadDashboard()
    } else {
      toast.error(json.message || '删除失败')
    }
  } catch (e) {
    toast.error('删除失败')
  }
}

function formatAction(action) {
  const map = {
    'login_success': '登录成功',
    'login_fail': '登录失败',
    'register': '注册',
    'user_create': '创建用户',
    'user_update': '更新用户',
    'user_delete': '删除用户',
    'user_reset_password': '重置密码',
    'user_status_change': '状态变更',
    'role_assign': '分配角色',
    'role_remove': '移除角色',
    'role_create': '创建角色',
    'role_update': '更新角色',
    'role_delete': '删除角色',
  }
  return map[action] || action
}

function getActionClass(action) {
  if (action?.includes('success') || action?.includes('create') || action?.includes('login')) return 'ok'
  if (action?.includes('fail') || action?.includes('delete') || action?.includes('remove')) return 'fail'
  return ''
}

function switchTab(newTab) {
  tab.value = newTab
  if (newTab === 'users') loadUsers()
  if (newTab === 'logs') loadLogs()
  if (newTab === 'dashboard') loadDashboard()
}
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="管理后台" tag="h1" />
      <p class="page-sub">
        欢迎，{{ authStore.user?.nickname || authStore.user?.username }}
        <span class="admin-badge">管理员</span>
      </p>
    </div>

    <div class="tab-bar">
      <button :class="{ active: tab === 'dashboard' }" @click="switchTab('dashboard')">仪表盘</button>
      <button :class="{ active: tab === 'users' }" @click="switchTab('users')">用户管理</button>
      <button :class="{ active: tab === 'logs' }" @click="switchTab('logs')">操作日志</button>
    </div>

    <div v-if="!isAdmin" class="no-permission">
      <p>你没有管理员权限</p>
    </div>

    <!-- ===== 仪表盘 ===== -->
    <div v-else-if="tab === 'dashboard'" class="dashboard">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon users">👥</div>
          <div class="stat-info">
            <span class="stat-val">{{ dashboard?.totalUsers || 0 }}</span>
            <span class="stat-lbl">总用户数</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon new">✨</div>
          <div class="stat-info">
            <span class="stat-val">{{ dashboard?.todayNewUsers || 0 }}</span>
            <span class="stat-lbl">今日新增</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon active">📈</div>
          <div class="stat-info">
            <span class="stat-val">{{ dashboard?.activeUsers7d || 0 }}</span>
            <span class="stat-lbl">7日活跃</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon vocab">📚</div>
          <div class="stat-info">
            <span class="stat-val">{{ dashboard?.totalVocabulary || 0 }}</span>
            <span class="stat-lbl">词汇总量</span>
          </div>
        </div>
      </div>

      <div class="dash-row">
        <div class="dash-panel">
          <h3 class="panel-title">词汇语言分布</h3>
          <div class="lang-bars">
            <div v-for="item in dashboard?.vocabularyByLang || []" :key="item.lang_code" class="lang-bar-item">
              <span class="lang-name">{{ LANG_NAMES[item.lang_code] || item.lang_code }}</span>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: Math.min(100, (item.count / (dashboard?.totalVocabulary || 1)) * 100) + '%' }"></div>
              </div>
              <span class="lang-count">{{ item.count }}</span>
            </div>
          </div>
        </div>

        <div class="dash-panel">
          <h3 class="panel-title">用户语言分布</h3>
          <div class="lang-bars">
            <div v-for="item in dashboard?.userLangDistribution || []" :key="item.lang" class="lang-bar-item">
              <span class="lang-name">{{ LANG_NAMES[item.lang] || item.lang || '未设置' }}</span>
              <div class="bar-track">
                <div class="bar-fill alt" :style="{ width: Math.min(100, (item.count / (dashboard?.totalUsers || 1)) * 100) + '%' }"></div>
              </div>
              <span class="lang-count">{{ item.count }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="dash-panel">
        <h3 class="panel-title">最近注册用户</h3>
        <div v-if="dashboard?.recentUsers?.length" class="recent-users">
          <div v-for="u in dashboard.recentUsers" :key="u.id" class="recent-user-item">
            <div class="ru-avatar">{{ (u.nickname || u.username)[0]?.toUpperCase() }}</div>
            <div class="ru-info">
              <span class="ru-name">{{ u.nickname || u.username }}</span>
              <span class="ru-time">{{ (u.create_time || '').substring(0, 16) }}</span>
            </div>
          </div>
        </div>
        <p v-else class="empty-text">暂无数据</p>
      </div>
    </div>

    <!-- ===== 用户管理 ===== -->
    <div v-else-if="tab === 'users'">
      <div class="toolbar">
        <input v-model="userSearch" class="search-input" placeholder="搜索用户名/昵称/邮箱..." @keyup.enter="loadUsers" />
        <button class="search-btn" @click="loadUsers">搜索</button>
        <button class="add-btn" @click="openCreateUser">+ 新建用户</button>
        <span class="user-count">共 {{ totalUsers }} 个用户</span>
      </div>

      <div v-if="loading" class="loading-wrap">加载中...</div>
      <div v-else-if="users.length" class="user-cards">
        <div v-for="u in users" :key="u.id" class="user-card" :class="{ disabled: u.status === 0 }">
          <div class="uc-top">
            <div class="uc-avatar">{{ (u.nickname || u.username)[0]?.toUpperCase() }}</div>
            <div class="uc-info">
              <div class="uc-name">
                {{ u.nickname || u.username }}
                <span class="uc-id">#{{ u.id }}</span>
                <span v-if="u.roles?.includes('ROLE_ADMIN')" class="role-tag admin">管理员</span>
                <span v-else class="role-tag user">普通用户</span>
                <span v-if="u.status === 0" class="role-tag disabled">已禁用</span>
              </div>
              <div class="uc-meta">
                <span>{{ u.email || '未设置邮箱' }}</span>
                <span class="uc-meta-divider">·</span>
                <span>{{ LANG_NAMES[u.currentLangCode] || u.currentLangCode || '未选语言' }}</span>
                <span class="uc-meta-divider">·</span>
                <span>{{ u.currentLevel || '未设置等级' }}</span>
              </div>
            </div>
          </div>

          <div class="uc-body">
            <div class="uc-stats">
              <div class="uc-stat">
                <span class="uc-stat-val">{{ u.totalWordsLearned || 0 }}</span>
                <span class="uc-stat-lbl">已学词汇</span>
              </div>
              <div class="uc-stat">
                <span class="uc-stat-val">{{ u.totalStudyDays || 0 }}</span>
                <span class="uc-stat-lbl">学习天数</span>
              </div>
              <div class="uc-stat">
                <span class="uc-stat-val">{{ u.points || 0 }}</span>
                <span class="uc-stat-lbl">积分</span>
              </div>
            </div>
          </div>

          <div class="uc-footer">
            <div class="uc-role-section">
              <span class="uc-section-label">角色：</span>
              <span v-for="r in roles" :key="r.id" class="role-chip"
                :class="{ active: u.roles?.includes(r.code) }"
                @click="toggleRole(u.id, r.id)">{{ r.name }}</span>
            </div>
            <div class="uc-actions">
              <button class="action-btn" @click="openEditUser(u)" title="编辑">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
              </button>
              <button class="action-btn" @click="openResetPassword(u.id)" title="重置密码">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
              </button>
              <button class="action-btn" :class="u.status === 0 ? 'enable' : 'disable'" @click="toggleUserStatus(u)" :title="u.status === 0 ? '启用' : '禁用'">
                <svg v-if="u.status === 0" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>
              </button>
              <button class="action-btn delete" @click="deleteUser(u.id)" title="删除">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-text">暂无用户数据</div>

      <div v-if="totalUsers > userPageSize" class="pager-wrap">
        <button :disabled="userPage <= 1" @click="userPage--; loadUsers()">← 上一页</button>
        <span class="page-info">第 {{ userPage }} 页 / 共 {{ Math.ceil(totalUsers / userPageSize) }} 页</span>
        <button :disabled="userPage >= Math.ceil(totalUsers / userPageSize)" @click="userPage++; loadUsers()">下一页 →</button>
      </div>
    </div>

    <!-- ===== 操作日志 ===== -->
    <div v-else-if="tab === 'logs'">
      <div class="toolbar">
        <input v-model="logSearch" class="search-input" placeholder="搜索日志..." @keyup.enter="loadLogs" />
        <button class="search-btn" @click="loadLogs">搜索</button>
        <span class="user-count">共 {{ totalLogs }} 条记录</span>
      </div>

      <div v-if="loading" class="loading-wrap">加载中...</div>
      <div v-else-if="logs.length" class="log-list">
        <div v-for="l in logs" :key="l.id" class="log-item">
          <div class="log-icon" :class="{
            icon_login: l.action?.includes('login'),
            icon_register: l.action?.includes('register') || l.action?.includes('user_create'),
            icon_role: l.action?.includes('role'),
            icon_delete: l.action?.includes('delete') || l.action?.includes('remove'),
          }">
            <span v-if="l.action?.includes('login')">→</span>
            <span v-else-if="l.action?.includes('register') || l.action?.includes('create')">+</span>
            <span v-else-if="l.action?.includes('role')">⚙</span>
            <span v-else-if="l.action?.includes('delete') || l.action?.includes('remove')">✕</span>
            <span v-else>●</span>
          </div>
          <div class="log-body">
            <div class="log-top">
              <span class="log-user">{{ l.detail }}</span>
              <span class="log-action" :class="getActionClass(l.action)">{{ formatAction(l.action) }}</span>
            </div>
            <div class="log-bottom">
              <span class="log-module-tag">{{ l.module }}</span>
              <span class="log-time">{{ (l.createdAt || l.created_at || '').substring(0, 16) }}</span>
            </div>
          </div>
        </div>
      </div>

      <p v-else class="empty-text">暂无日志数据</p>

      <div v-if="totalLogs > logPageSize" class="pager-wrap">
        <button :disabled="logPage <= 1" @click="logPage--; loadLogs()">← 上一页</button>
        <span class="page-info">第 {{ logPage }} 页 / 共 {{ Math.ceil(totalLogs / logPageSize) }} 页</span>
        <button :disabled="logPage >= Math.ceil(totalLogs / logPageSize)" @click="logPage++; loadLogs()">下一页 →</button>
      </div>
    </div>

    <!-- 用户编辑弹窗 -->
    <div v-if="showUserModal" class="modal-overlay" @click.self="showUserModal = false">
      <div class="modal">
        <h3>{{ editingUser?.id ? '编辑用户' : '新建用户' }}</h3>
        <div class="form-group">
          <label>用户名</label>
          <input v-model="editingUser.username" :disabled="!!editingUser.id" placeholder="请输入用户名" />
        </div>
        <div v-if="!editingUser?.id" class="form-group">
          <label>初始密码</label>
          <input v-model="editingUser.password" type="text" placeholder="请输入初始密码" />
        </div>
        <div class="form-group">
          <label>昵称</label>
          <input v-model="editingUser.nickname" placeholder="请输入昵称" />
        </div>
        <div class="form-group">
          <label>邮箱</label>
          <input v-model="editingUser.email" placeholder="请输入邮箱" />
        </div>
        <div class="modal-actions">
          <button class="cancel-btn" @click="showUserModal = false">取消</button>
          <button class="confirm-btn" @click="saveUser">{{ editingUser?.id ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- 重置密码弹窗 -->
    <div v-if="showResetPwdModal" class="modal-overlay" @click.self="showResetPwdModal = false">
      <div class="modal">
        <h3>重置密码</h3>
        <div class="form-group">
          <label>新密码</label>
          <input v-model="newPassword" type="text" placeholder="请输入新密码（至少6位）" />
        </div>
        <div class="modal-actions">
          <button class="cancel-btn" @click="showResetPwdModal = false">取消</button>
          <button class="confirm-btn" @click="confirmResetPassword">确认重置</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 960px; margin: 0 auto; padding: 0 16px 60px; }
.page-header { text-align: center; padding: 20px 0 8px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); }
.page-sub { font-size: 14px; color: var(--color-text-muted); display: flex; align-items: center; gap: 8px; justify-content: center; }
.admin-badge { font-size: 11px; padding: 2px 10px; border-radius: 8px; background: rgba(231,76,60,0.08); color: #e74c3c; font-weight: 600; }
.empty-text { text-align: center; padding: 40px; color: #aaa; }
.no-permission { text-align: center; padding: 60px; color: #999; font-size: 16px; }
.loading-wrap { text-align: center; padding: 40px; color: #888; }

.tab-bar { display: flex; gap: 8px; justify-content: center; margin: 16px 0 24px; }
.tab-bar button {
  padding: 8px 20px; border-radius: 20px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600; color: #666; cursor: pointer;
  transition: all 0.2s;
}
.tab-bar button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }

.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.search-input {
  flex: 1; min-width: 200px; padding: 10px 14px; border-radius: 10px; border: 1.5px solid #e0e0e0;
  background: #fafafa; font-size: 14px; outline: none;
}
.search-input:focus { border-color: #7c9db5; }
.search-btn, .add-btn {
  padding: 10px 18px; border-radius: 10px; border: none; color: #fff;
  font-size: 13px; font-weight: 600; cursor: pointer;
}
.search-btn { background: #5a7d96; }
.add-btn { background: #27ae60; }
.user-count { font-size: 13px; color: #aaa; margin-left: auto; }

/* 仪表盘 */
.dashboard { display: flex; flex-direction: column; gap: 20px; }
.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.stat-card {
  display: flex; align-items: center; gap: 14px;
  background: rgba(255,255,255,0.8); backdrop-filter: blur(14px);
  border: 1px solid rgba(0,0,0,0.04); border-radius: 14px; padding: 18px 20px;
}
.stat-icon {
  width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  font-size: 24px;
}
.stat-icon.users { background: rgba(90,125,150,0.1); }
.stat-icon.new { background: rgba(39,174,96,0.1); }
.stat-icon.active { background: rgba(240,151,92,0.1); }
.stat-icon.vocab { background: rgba(155,89,182,0.1); }
.stat-info { display: flex; flex-direction: column; }
.stat-val { font-size: 24px; font-weight: 800; color: var(--color-text); }
.stat-lbl { font-size: 12px; color: #888; margin-top: 2px; }

.dash-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 600px) { .dash-row { grid-template-columns: 1fr; } }
.dash-panel {
  background: rgba(255,255,255,0.8); backdrop-filter: blur(14px);
  border: 1px solid rgba(0,0,0,0.04); border-radius: 14px; padding: 18px 20px;
}
.panel-title { font-size: 15px; font-weight: 700; color: var(--color-text); margin: 0 0 14px; }

.lang-bars { display: flex; flex-direction: column; gap: 10px; }
.lang-bar-item { display: flex; align-items: center; gap: 10px; }
.lang-name { font-size: 13px; color: #666; width: 60px; flex-shrink: 0; }
.bar-track { flex: 1; height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.bar-fill { height: 100%; background: linear-gradient(90deg, #7c9db5, #5a7d96); border-radius: 4px; transition: width 0.3s; }
.bar-fill.alt { background: linear-gradient(90deg, #27ae60, #2ecc71); }
.lang-count { font-size: 12px; color: #888; width: 50px; text-align: right; flex-shrink: 0; }

.recent-users { display: flex; flex-direction: column; gap: 8px; }
.recent-user-item { display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 8px; }
.recent-user-item:hover { background: #f8fafb; }
.ru-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700;
}
.ru-info { display: flex; flex-direction: column; }
.ru-name { font-size: 13px; font-weight: 600; color: var(--color-text); }
.ru-time { font-size: 11px; color: #aaa; }

/* 用户卡片 */
.user-cards { display: flex; flex-direction: column; gap: 12px; }
.user-card {
  background: rgba(255,255,255,0.8); backdrop-filter: blur(14px);
  border: 1px solid rgba(0,0,0,0.04); border-radius: 14px; padding: 18px 20px;
  transition: all 0.2s;
}
.user-card.disabled { opacity: 0.5; }
.uc-top { display: flex; gap: 14px; margin-bottom: 14px; }
.uc-avatar {
  width: 44px; height: 44px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700;
}
.uc-info { flex: 1; min-width: 0; }
.uc-name { font-size: 16px; font-weight: 700; color: var(--color-text); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.uc-id { font-size: 12px; color: #bbb; font-weight: 400; }
.role-tag { font-size: 10px; padding: 2px 8px; border-radius: 6px; font-weight: 600; }
.role-tag.admin { background: rgba(231,76,60,0.08); color: #e74c3c; }
.role-tag.user { background: rgba(90,125,150,0.08); color: #5a7d96; }
.role-tag.disabled { background: rgba(170,170,170,0.1); color: #999; }
.uc-meta { font-size: 12px; color: #888; margin-top: 2px; display: flex; gap: 4px; flex-wrap: wrap; }
.uc-meta-divider { color: #ddd; }

.uc-stats { display: flex; gap: 12px; }
.uc-stat { flex: 1; text-align: center; padding: 10px; background: #f8fafb; border-radius: 8px; }
.uc-stat-val { display: block; font-size: 16px; font-weight: 700; color: var(--color-text); }
.uc-stat-lbl { font-size: 11px; color: #999; }

.uc-footer {
  display: flex; align-items: center; gap: 12px; margin-top: 12px; padding-top: 12px;
  border-top: 1px solid rgba(0,0,0,0.04); flex-wrap: wrap;
}
.uc-role-section { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; flex: 1; }
.uc-section-label { font-size: 11px; color: #aaa; white-space: nowrap; }
.role-chip {
  padding: 3px 10px; border-radius: 6px; border: 1px solid #ddd;
  background: #fff; font-size: 11px; color: #888; cursor: pointer; transition: all 0.2s;
}
.role-chip.active { background: #5a7d96; color: #fff; border-color: #5a7d96; font-weight: 600; }
.role-chip:hover:not(.active) { border-color: #5a7d96; }

.uc-actions { display: flex; gap: 6px; }
.action-btn {
  width: 30px; height: 30px; border-radius: 8px; border: 1.5px solid #ddd;
  background: #fff; color: #888; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.action-btn:hover { border-color: #5a7d96; color: #5a7d96; background: #f5f9fb; }
.action-btn.delete:hover { border-color: #e74c3c; color: #e74c3c; background: #fef5f5; }
.action-btn.disable:hover { border-color: #f0975c; color: #f0975c; background: #fff8f0; }
.action-btn.enable:hover { border-color: #27ae60; color: #27ae60; background: #f0faf3; }

.pager-wrap { display: flex; justify-content: center; align-items: center; gap: 12px; margin-top: 20px; }
.pager-wrap button {
  padding: 8px 16px; border-radius: 8px; border: 1.5px solid #ddd;
  background: #fff; font-size: 13px; color: #666; cursor: pointer;
}
.pager-wrap button:disabled { opacity: 0.3; cursor: not-allowed; }
.page-info { font-size: 13px; color: #888; }

/* 日志 */
.log-list { display: flex; flex-direction: column; gap: 6px; }
.log-item {
  display: flex; gap: 12px; padding: 12px 14px;
  border-radius: 10px; background: rgba(255,255,255,0.75);
  border: 1px solid rgba(0,0,0,0.04); align-items: flex-start;
}
.log-icon {
  width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: #f0f0f0; color: #aaa; font-size: 14px; font-weight: 700;
}
.log-icon.icon_login { background: rgba(39,174,96,0.08); color: #27ae60; }
.log-icon.icon_register { background: rgba(90,125,150,0.08); color: #5a7d96; }
.log-icon.icon_role { background: rgba(240,151,92,0.08); color: #f0975c; }
.log-icon.icon_delete { background: rgba(231,76,60,0.08); color: #e74c3c; }
.log-body { flex: 1; min-width: 0; }
.log-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; gap: 8px; }
.log-user { font-size: 13px; font-weight: 600; color: var(--color-text); }
.log-action { font-size: 12px; padding: 2px 8px; border-radius: 6px; font-weight: 600; white-space: nowrap; }
.log-action.ok { background: rgba(39,174,96,0.06); color: #27ae60; }
.log-action.fail { background: rgba(231,76,60,0.06); color: #e74c3c; }
.log-bottom { display: flex; align-items: center; gap: 8px; }
.log-module-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; background: rgba(0,0,0,0.04); color: #aaa; }
.log-time { font-size: 11px; color: #bbb; }

/* 弹窗 */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
  padding: 20px;
}
.modal {
  background: #fff; border-radius: 16px; padding: 24px;
  width: 100%; max-width: 400px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.modal h3 { margin: 0 0 20px; font-size: 18px; font-weight: 700; color: var(--color-text); }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 13px; font-weight: 600; color: #555; margin-bottom: 6px; }
.form-group input {
  width: 100%; padding: 10px 14px; border-radius: 10px; border: 1.5px solid #e0e0e0;
  background: #fafafa; font-size: 14px; outline: none; box-sizing: border-box;
}
.form-group input:focus { border-color: #7c9db5; }
.form-group input:disabled { background: #f0f0f0; color: #999; cursor: not-allowed; }
.modal-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 20px; }
.cancel-btn {
  padding: 10px 20px; border-radius: 10px; border: 1.5px solid #ddd;
  background: #fff; color: #666; font-size: 14px; font-weight: 600; cursor: pointer;
}
.confirm-btn {
  padding: 10px 20px; border-radius: 10px; border: none;
  background: #5a7d96; color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}
</style>
