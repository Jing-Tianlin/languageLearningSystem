<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getLevelLabel } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import { userApi } from '@/api/user'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'

const router = useRouter()
const authStore = useAuthStore()
const BASE = API_BASE_URL

const form = ref({ nickname: '', email: '', phone: '' })
const msg = ref('')
const error = ref('')
const editMode = ref(false)
const showDeleteConfirm = ref(false)

// 从后端获取实时统计
const stats = ref({ totalWords: 0, masteredWords: 0, masteryRate: 0, totalReviews: 0 })
const currentLevelLabel = computed(() => getLevelLabel(authStore.targetLanguage, authStore.targetLevel))

async function loadStats() {
  if (!authStore.user) return
  try {
    const res = await fetch(`${BASE}/stats/overview?userId=${authStore.user.id}`)
    const json = await res.json()
    if (json.code === 200 && json.data) stats.value = json.data
  } catch (e) {}
}

async function deleteAccount() {
  try {
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
  try {
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
      <!-- 左侧：头像 + 基本信息卡片 -->
      <div class="profile-sidebar">
        <div class="avatar-section">
          <div class="profile-avatar">
            {{ (authStore.user?.nickname || authStore.user?.username || 'U')[0].toUpperCase() }}
          </div>
          <h2 class="profile-name">{{ authStore.user?.nickname || authStore.user?.username }}</h2>
          <span class="profile-badge">{{ authStore.user?.roles || 'USER' }}</span>
        </div>

        <!-- 统计卡片 -->
        <div class="stats-mini">
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.totalWords || 0 }}</span>
            <span class="smi-lbl">学习词汇</span>
          </div>
          <div class="stat-mini-item">
            <span class="smi-num accent">{{ stats.masteredWords || 0 }}</span>
            <span class="smi-lbl">已掌握</span>
          </div>
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.masteryRate || 0 }}%</span>
            <span class="smi-lbl">掌握率</span>
          </div>
          <div class="stat-mini-item">
            <span class="smi-num">{{ stats.totalReviews || 0 }}</span>
            <span class="smi-lbl">总复习次数</span>
          </div>
        </div>
      </div>

      <!-- 右侧：编辑表单 -->
      <div class="profile-main">
        <div class="profile-card">
          <div class="card-header">
            <h3>账号信息</h3>
            <button
              v-if="!editMode"
              class="edit-toggle"
              @click="editMode = true"
            >编辑</button>
          </div>

          <form v-if="editMode" class="profile-form" @submit.prevent="save">
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
              <button type="button" class="cancel-btn" @click="editMode = false">取消</button>
              <button type="submit" class="save-btn">保存修改</button>
            </div>
          </form>

          <!-- 只读模式 -->
          <div v-else class="profile-info">
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

          <!-- 退出登录 -->
          <button class="logout-btn" @click="authStore.logout(); router.push('/login')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            退出登录
          </button>

          <!-- 注销账户 -->
          <button class="delete-btn" @click="showDeleteConfirm = true">
            注销账户
          </button>

          <!-- 确认注销弹窗 -->
          <Teleport to="body">
            <div v-if="showDeleteConfirm" class="confirm-overlay" @click.self="showDeleteConfirm = false">
              <div class="confirm-card">
                <h4>确认注销</h4>
                <p>注销后你的所有学习数据将被永久删除，无法恢复。确定要继续吗？</p>
                <div class="confirm-actions">
                  <button class="cancel-btn" @click="showDeleteConfirm = false">取消</button>
                  <button class="confirm-delete-btn" @click="deleteAccount()">确认注销</button>
                </div>
              </div>
            </div>
          </Teleport>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 页面头部 ===== */
.page-header {
  text-align: center;
  padding: 24px 0 10px;
}
.page-header :deep(.letter-swap-title) {
  font-size: 30px;
  font-weight: 800;
  color: var(--color-text);
  margin-bottom: 6px;
}
.page-sub {
  font-size: 14px;
  color: var(--color-text-muted);
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
  gap: 20px;
}

.avatar-section {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-lg);
  padding: 32px 24px;
  text-align: center;
}

.profile-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 800;
  color: #fff;
  margin: 0 auto 16px;
  box-shadow: 0 6px 20px rgba(90, 125, 150, 0.3);
}

.profile-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 6px;
}

.profile-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 12px;
  border-radius: 20px;
  background: rgba(124, 157, 181, 0.15);
  color: #5a7d96;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 迷你统计 */
.stats-mini {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px;
  margin-top: 14px;
  border-radius: var(--radius-md);
  border: 1.5px solid #e8dddd;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  color: #c0392b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s;
}
.logout-btn:hover {
  background: #fef5f5;
  border-color: #e74c3c;
  color: #e74c3c;
}

.stat-mini-item {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  background: rgba(255,255,255,0.6); backdrop-filter: blur(10px);
  border: 1px solid rgba(0,0,0,0.04); border-radius: var(--radius-md);
  padding: 14px 12px; text-align: center;
}
.smi-num { font-size: 20px; font-weight: 700; color: var(--color-text); }
.smi-num.accent { color: #27ae60; }
.smi-lbl { font-size: 11px; color: #999; margin-top: 2px; }

/* ===== 右侧主区域 ===== */
.profile-main {
  flex: 1;
  min-width: 0;
}

.profile-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-lg);
  padding: 28px 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.card-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
}
.edit-toggle {
  padding: 7px 20px;
  border-radius: 8px;
  border: 1.5px solid #7c9db5;
  background: transparent;
  color: #5a7d96;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
}
.edit-toggle:hover {
  background: rgba(124, 157, 181, 0.1);
}

/* ===== 表单 ===== */
.profile-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-row {
  display: flex;
  gap: 16px;
}
@media (max-width: 480px) {
  .form-row { flex-direction: column; }
}
.form-row .form-group {
  flex: 1;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: #555;
}

.form-group input,
.form-group select {
  padding: 11px 15px;
  border-radius: 10px;
  border: 1.5px solid #e5e5e5;
  background: #fafafa;
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.25s, box-shadow 0.25s;
}

.form-group input:focus,
.form-group select:focus {
  border-color: #7c9db5;
  box-shadow: 0 0 0 3px rgba(124, 157, 181, 0.1);
}

.form-group select option {
  background: #fff;
  color: var(--color-text);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.cancel-btn {
  padding: 10px 22px;
  border-radius: 10px;
  border: 1.5px solid #ddd;
  background: #fff;
  color: #666;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s;
}
.cancel-btn:hover {
  background: #f5f5f5;
}

.save-btn {
  padding: 10px 26px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 3px 12px rgba(90, 125, 150, 0.25);
}
.save-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(90, 125, 150, 0.35);
}

.form-msg {
  text-align: center;
  font-size: 13px;
  padding: 8px 14px;
  border-radius: 8px;
}
.form-msg.success {
  background: #eefaf3;
  color: #27ae60;
}
.form-msg.error {
  background: #fef0ef;
  color: #e74c3c;
}

/* ===== 只读信息 ===== */
.profile-info {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 14px;
  color: #888;
  font-weight: 500;
}

.info-value {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.level-tag {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 12px;
  background: rgba(124, 157, 181, 0.12);
  color: #5a7d96;
  font-weight: 600;
  font-size: 13px;
}

.empty-text {
  text-align: center;
  color: var(--color-text-muted);
  padding: 60px 0;
  font-size: 15px;
}
.empty-text a {
  color: var(--color-primary-dark);
  text-decoration: none;
  font-weight: 500;
}

/* 注销按钮 */
.delete-btn {
  display: flex; align-items: center; justify-content: center;
  width: 100%; padding: 12px; margin-top: 8px;
  border-radius: var(--radius-md); border: 1.5px solid #e8dddd;
  background: rgba(255,255,255,0.7); backdrop-filter: blur(10px);
  color: #e74c3c; font-size: 14px; font-weight: 500; cursor: pointer;
  transition: all 0.25s;
}
.delete-btn:hover { background: #fef5f5; border-color: #e74c3c; }

/* 确认弹窗 */
.confirm-overlay {
  position: fixed; inset: 0; z-index: 8000;
  background: rgba(0,0,0,0.2); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
}
.confirm-card {
  background: #fff; border-radius: 16px; width: 380px; max-width: 95vw;
  padding: 28px; text-align: center;
}
.confirm-card h4 { font-size: 18px; font-weight: 700; color: var(--color-text); margin: 0 0 10px; }
.confirm-card p { font-size: 14px; color: #888; line-height: 1.6; margin: 0 0 20px; }
.confirm-actions { display: flex; gap: 10px; justify-content: center; }
.cancel-btn {
  padding: 10px 22px; border-radius: 10px; border: 1.5px solid #ddd;
  background: #fff; color: #666; font-size: 14px; cursor: pointer;
}
.confirm-delete-btn {
  padding: 10px 22px; border-radius: 10px; border: none;
  background: #e74c3c; color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}
</style>
