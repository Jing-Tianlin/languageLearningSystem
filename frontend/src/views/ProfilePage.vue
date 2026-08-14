<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getLevelLabel } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'
import { userApi } from '@/api/user'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import { getReminderTime, setReminderTime, isReminderEnabled } from '@/composables/useStudyReminder'

const router = useRouter()
const authStore = useAuthStore()
const BASE = API_BASE_URL

const form = ref({ nickname: '', email: '', phone: '' })
const msg = ref('')
const error = ref('')
const editMode = ref(false)
const showDeleteConfirm = ref(false)
const statsError = ref(false)

// 每日学习提醒
const reminderTime = ref(getReminderTime())
const reminderEnabled = ref(isReminderEnabled())
const reminderPermission = ref(typeof Notification !== 'undefined' ? Notification.permission : 'unsupported')

function toggleReminder() {
  reminderEnabled.value = !reminderEnabled.value
  if (reminderEnabled.value) {
    if (reminderPermission.value === 'default') requestReminderPermission()
    if (!reminderTime.value) reminderTime.value = '20:00'
    setReminderTime(reminderTime.value)
  } else {
    setReminderTime('')
  }
}

async function requestReminderPermission() {
  if (typeof Notification === 'undefined' || reminderPermission.value !== 'default') return
  const perm = await Notification.requestPermission()
  reminderPermission.value = perm
  if (perm === 'granted' && reminderEnabled.value) setReminderTime(reminderTime.value)
}

function changeReminderTime() {
  if (reminderEnabled.value && reminderTime.value) setReminderTime(reminderTime.value)
}

// 从后端获取实时统计（userId 由 token 决定，无需传参）
const stats = ref({ totalWords: 0, masteredWords: 0, masteryRate: 0, totalReviews: 0 })
const currentLevelLabel = computed(() => getLevelLabel(authStore.targetLanguage, authStore.targetLevel))

// 角色友好显示
const roleLabel = computed(() => {
  const roles = authStore.user?.roles
  if (Array.isArray(roles)) {
    if (roles.some(r => r === 'ROLE_ADMIN' || r === 'ADMIN')) return '管理员'
    return '普通用户'
  }
  return '普通用户'
})

// 头像字母
const avatarLetter = computed(() =>
  (authStore.user?.nickname || authStore.user?.username || 'U').charAt(0).toUpperCase()
)

async function loadStats() {
  statsError.value = false
  if (!authStore.isLoggedIn) return
  try {
    const json = await fetchJson(`${BASE}/stats/overview`)
    if (json.code === 200 && json.data) stats.value = json.data
    else statsError.value = true
  } catch (e) {
    statsError.value = true
  }
}

async function deleteAccount() {
  try {
    // 后端校验路径 id 必须与 token 用户一致，故传本人 id
    await userApi.deleteUser(authStore.user.id)
    authStore.logout()
    showDeleteConfirm.value = false
    router.push('/login')
  } catch (e) {
    error.value = e.message || '注销失败'
  }
}

onMounted(async () => {
  if (authStore.isLoggedIn) {
    await authStore.fetchProfile()
    const u = authStore.user
    if (u) {
      form.value = { nickname: u.nickname || '', email: u.email || '', phone: u.phone || '' }
    }
    loadStats()
  }
})

async function save() {
  msg.value = ''; error.value = ''
  // 邮箱格式校验（选填）
  if (form.value.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    error.value = '邮箱格式不正确'
    return
  }
  try {
    // 后端校验 body id 必须与 token 用户一致，故传本人 id
    await userApi.updateUser({ id: authStore.user.id, ...form.value })
    msg.value = '保存成功'
    editMode.value = false
    await authStore.fetchProfile()
    setTimeout(() => msg.value = '', 2000)
  } catch (e) { error.value = e.message || '保存失败' }
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="个人中心" tag="h1" />
      <p class="page-sub">管理你的账号信息</p>
    </div>

    <div v-if="!authStore.isLoggedIn" class="empty-text">
      请先<a href="#" @click.prevent="router.push('/login')">登录</a>
    </div>

    <div v-else class="profile-layout">
      <!-- 左侧：头像 + 统计 + 退出登录 -->
      <div class="profile-sidebar">
        <div class="avatar-section">
          <div class="profile-avatar">
            {{ avatarLetter }}
          </div>
          <h2 class="profile-name">{{ authStore.user?.nickname || authStore.user?.username }}</h2>
          <span class="profile-badge">{{ roleLabel }}</span>
        </div>

        <!-- 统计卡片 2×2 -->
        <div class="stats-mini">
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.totalWords || 0 }}</span>
            <span class="smi-lbl">学习词汇</span>
          </div>
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.masteredWords || 0 }}</span>
            <span class="smi-lbl">已掌握</span>
          </div>
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.masteryRate || 0 }}%</span>
            <span class="smi-lbl">掌握率</span>
          </div>
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.totalReviews || 0 }}</span>
            <span class="smi-lbl">总复习</span>
          </div>
        </div>
        <p v-if="statsError" class="stats-error">统计加载失败</p>

        <!-- 注销账户：低频危险操作，放左侧边栏底部与统计对齐 -->
        <button class="delete-btn btn btn-danger btn-block" @click="showDeleteConfirm = true">注销账户</button>
      </div>

      <!-- 右侧：编辑表单 -->
      <div class="profile-main">
        <div class="profile-card">
          <div class="card-header">
            <h3>账号信息</h3>
            <button
              v-if="!editMode"
              class="edit-toggle btn btn-secondary btn-sm"
              @click="editMode = true"
            >编辑</button>
          </div>

          <form v-if="editMode" class="profile-form" @submit.prevent="save">
            <div class="form-group">
              <label>用户名</label>
              <input :value="authStore.user?.username" type="text" disabled />
            </div>
            <div class="form-group">
              <label>昵称</label>
              <input v-model="form.nickname" type="text" placeholder="给自己取个名字" />
            </div>
            <div class="form-group">
              <label>邮箱</label>
              <input v-model="form.email" type="email" placeholder="your@email.com" />
            </div>
            <div class="form-group">
              <label>手机号</label>
              <input v-model="form.phone" type="text" placeholder="选填" />
            </div>

            <p v-if="msg" class="form-msg success">{{ msg }}</p>
            <p v-if="error" class="form-msg error">{{ error }}</p>

            <div class="form-actions">
              <button type="button" class="cancel-btn btn btn-secondary" @click="editMode = false">取消</button>
              <button type="submit" class="save-btn btn btn-primary">保存修改</button>
            </div>
          </form>

          <!-- 只读模式 -->
          <div v-else class="profile-info">
            <div class="info-row">
              <span class="info-label">用户名</span>
              <span class="info-value">{{ authStore.user?.username || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">昵称</span>
              <span class="info-value">{{ form.nickname || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">邮箱</span>
              <span class="info-value">{{ form.email || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">手机号</span>
              <span class="info-value">{{ form.phone || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">当前学习等级</span>
              <span class="info-value">
                <span class="level-tag">{{ currentLevelLabel }}</span>
              </span>
            </div>
            <div class="info-row">
              <span class="info-label">注册时间</span>
              <span class="info-value">{{ formatDate(authStore.user?.createTime) }}</span>
            </div>
          </div>
        </div>

        <!-- 每日学习提醒 -->
        <div class="profile-card reminder-card">
          <div class="card-header">
            <h3>每日学习提醒</h3>
          </div>
          <div class="reminder-row">
            <div class="reminder-info">
              <p class="reminder-desc">到点提醒你完成今日学习目标，保持学习节奏</p>
              <p v-if="reminderPermission === 'default'" class="reminder-hint">
                <button class="link-btn" @click="requestReminderPermission">授权浏览器通知</button>
                <template v-if="reminderPermission === 'denied'">（通知已被浏览器禁用）</template>
              </p>
              <p v-else-if="reminderPermission === 'denied'" class="reminder-hint warn">通知权限被禁用，请在浏览器设置中开启</p>
            </div>
            <div class="reminder-controls">
              <input
                v-if="reminderEnabled"
                type="time"
                v-model="reminderTime"
                class="time-input"
                @change="changeReminderTime"
              />
              <button
                class="btn"
                :class="reminderEnabled ? 'btn-primary btn-sm' : 'btn-ghost btn-sm'"
                @click="toggleReminder"
              >{{ reminderEnabled ? '已开启' : '开启提醒' }}</button>
            </div>
          </div>
        </div>

        <!-- 退出登录：高频操作，主卡片下方 -->
        <div class="action-zone">
          <button class="logout-btn btn btn-ghost btn-block" @click="authStore.logout(); router.push('/login')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            退出登录
          </button>
        </div>
      </div>
    </div>

    <!-- 确认注销弹窗 -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="confirm-overlay" @click.self="showDeleteConfirm = false">
        <div class="confirm-card">
          <div class="confirm-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
          <h4>确认注销</h4>
          <p>注销后你的所有学习数据将被永久删除，无法恢复。确定要继续吗？</p>
          <div class="confirm-actions">
            <button class="cancel-btn btn btn-secondary" @click="showDeleteConfirm = false">取消</button>
            <button class="confirm-delete-btn btn btn-danger-solid" @click="deleteAccount()">确认注销</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ===== 页面头部 ===== */
.page-header {
  text-align: center;
  padding: 28px 0 12px;
}
.page-header :deep(.letter-swap-title) {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: -0.3px;
  margin-bottom: 6px;
  font-family: var(--font-heading);
}
.page-sub {
  font-size: 14px;
  color: var(--color-text-muted);
  letter-spacing: 0.5px;
}

/* ===== 布局：左侧边栏 + 右侧主区域 ===== */
.profile-layout {
  display: flex;
  gap: 28px;
  max-width: 960px;
  margin: 0 auto;
  padding-top: 20px;
  align-items: flex-start;
}
@media (max-width: 768px) {
  .profile-layout {
    flex-direction: column;
  }
}

/* ===== 左侧边栏 ===== */
.profile-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.avatar-section {
  background: var(--color-bg-card);
  backdrop-filter: blur(12px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 34px 24px;
  text-align: center;
  box-shadow: var(--shadow-sm);
}

.profile-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--color-gold);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 600;
  color: #fff;
  margin: 0 auto 16px;
  box-shadow: var(--shadow-gold);
}

.profile-name {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 10px;
  font-family: var(--font-heading);
}

.profile-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  padding: 4px 14px;
  border-radius: var(--radius-full);
  background: rgba(255, 107, 107, 0.1);
  color: var(--color-primary);
  letter-spacing: 0.6px;
}

/* 迷你统计 2×2 */
.stats-mini {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.stat-mini-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px 10px;
  text-align: center;
  box-shadow: var(--shadow-xs);
}
.smi-num {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text);
  font-family: var(--font-number);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.smi-lbl { font-size: 11px; color: var(--color-text-muted); margin-top: 2px; letter-spacing: 0.4px; }

.stats-error {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* 退出登录：中性描边，无破坏性 */
.logout-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ===== 右侧主区域 ===== */
.profile-main {
  flex: 1;
  min-width: 0;
}

.profile-card {
  background: var(--color-bg-card);
  backdrop-filter: blur(14px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 30px 32px;
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 26px;
}
.card-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.edit-toggle {
  font-size: 13px;
}

/* ===== 表单 ===== */
.profile-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.form-group label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  letter-spacing: 0.3px;
}

.form-group input {
  padding: 12px 15px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-hover);
  background: #fff;
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.25s, box-shadow 0.25s;
}

.form-group input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(255, 107, 107, 0.1);
}

.form-group input:disabled {
  background: #fffaf0;
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 4px;
}

.form-msg {
  text-align: center;
  font-size: 13px;
  padding: 9px 14px;
  border-radius: var(--radius-sm);
}
.form-msg.success {
  background: #f0fae9;
  color: #3fa65a;
}
.form-msg.error {
  background: #fff0ed;
  color: #a85a4c;
}

/* ===== 只读信息 ===== */
.profile-info {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 0;
  border-bottom: 1px solid var(--color-border);
}
.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.info-value {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.level-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  background: rgba(255, 107, 107, 0.08);
  color: var(--color-primary);
  font-weight: 600;
  font-size: 13px;
}

/* ===== 操作区：退出登录（主卡片下方） ===== */
.action-zone {
  margin-top: 16px;
}
.delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ===== 每日学习提醒 ===== */
.reminder-card { margin-top: 16px; }
.reminder-card .card-header { margin-bottom: 14px; }
.reminder-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.reminder-info { flex: 1; min-width: 0; }
.reminder-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.6;
  margin: 0 0 6px;
}
.reminder-hint { font-size: 12px; color: var(--color-primary); margin: 0; }
.reminder-hint.warn { color: #ff6b6b; }
.link-btn {
  background: none;
  border: none;
  padding: 0;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
}
.link-btn:hover { color: var(--color-primary-deep); }
.reminder-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.time-input {
  padding: 7px 10px;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--color-border);
  background: var(--color-bg);
  font-size: 14px;
  color: var(--color-text);
  outline: none;
}
.time-input:focus { border-color: var(--color-primary); }

/* ===== 确认弹窗 ===== */
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 8000;
  background: rgba(42, 36, 56, 0.28);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.confirm-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  width: 380px;
  max-width: 95vw;
  padding: 34px 30px 26px;
  text-align: center;
  box-shadow: var(--shadow-lg);
}
.confirm-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: #fff0ed;
  color: #a85a4c;
  display: flex;
  align-items: center;
  justify-content: center;
}
.confirm-card h4 { font-size: 18px; font-weight: 600; color: var(--color-text); margin: 0 0 10px; font-family: var(--font-heading); }
.confirm-card p { font-size: 14px; color: var(--color-text-secondary); line-height: 1.7; margin: 0 0 26px; }
.confirm-actions { display: flex; gap: 12px; justify-content: center; }
.confirm-delete-btn {
  font-size: 14px;
}

.empty-text {
  text-align: center;
  color: var(--color-text-muted);
  padding: 60px 0;
  font-size: 15px;
}
.empty-text a {
  color: var(--color-gold);
  text-decoration: none;
  font-weight: 500;
}
</style>
