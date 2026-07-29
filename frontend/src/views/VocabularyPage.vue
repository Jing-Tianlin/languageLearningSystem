<script setup>
import { onMounted, ref, watch, computed } from 'vue'
import { useVocabularyStore } from '@/stores/vocabulary'
import { useLanguageStore } from '@/stores/language'
import { useCourseStore } from '@/stores/course'
import { useFavoriteStore } from '@/stores/favorite'
import { useAuthStore } from '@/stores/auth'
import { vocabularyApi } from '@/api/vocabulary'
import { toast } from '@/composables/useToast'
import { getExamLevels } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import VocabCard from '@/components/cards/VocabCard.vue'
import VocabFilter from '@/components/common/VocabFilter.vue'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import Pagination from '@/components/common/Pagination.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const vocabularyStore = useVocabularyStore()
const languageStore = useLanguageStore()
const courseStore = useCourseStore()
const favoriteStore = useFavoriteStore()
const authStore = useAuthStore()

const pageNo = ref(1)
const pageSize = 12
const filter = ref({ partOfSpeech: '', word: '' })
const selectedLevel = ref(null)

const showAddModal = ref(false)
const showDetailModal = ref(false)
const showEditModal = ref(false)
const addForm = ref({ word: '', definition: '', partOfSpeech: '', phonetic: '', exampleSentence: '', exampleTranslation: '', langCode: '', level: '' })
const editForm = ref({ id: '', word: '', definition: '', partOfSpeech: '', phonetic: '', exampleSentence: '', exampleTranslation: '', langCode: '', level: '' })
const detailWord = ref(null)
const addSaving = ref(false)
const editSaving = ref(false)

function openAddModal() {
  addForm.value = {
    word: '', definition: '', partOfSpeech: '', phonetic: '',
    exampleSentence: '', exampleTranslation: '',
    langCode: authStore.targetLanguage || filter.value.langCode || 'en',
    level: selectedLevel.value || '',
  }
  showAddModal.value = true
}

function openDetailModal(vocab) {
  detailWord.value = vocab
  showDetailModal.value = true
}

function openEditModal(vocab) {
  editForm.value = {
    id: vocab.id,
    word: vocab.word,
    definition: vocab.definition,
    partOfSpeech: vocab.partOfSpeech || '',
    phonetic: vocab.phonetic || '',
    exampleSentence: vocab.exampleSentence || '',
    exampleTranslation: vocab.exampleTranslation || '',
    langCode: vocab.langCode,
    level: vocab.level || '',
  }
  showEditModal.value = true
}

async function doCreate() {
  const f = addForm.value
  if (!f.word.trim() || !f.definition.trim()) {
    toast.warning('单词和释义不能为空')
    return
  }
  if (!f.langCode) {
    toast.warning('请选择语言')
    return
  }
  addSaving.value = true
  try {
    await vocabularyApi.createVocabulary({
      word: f.word.trim(),
      definition: f.definition.trim(),
      partOfSpeech: f.partOfSpeech || null,
      phonetic: f.phonetic || null,
      exampleSentence: f.exampleSentence || null,
      exampleTranslation: f.exampleTranslation || null,
      langCode: f.langCode,
      level: f.level || null,
    })
    toast.success(`「${f.word}」已添加`)
    showAddModal.value = false
    loadData()
  } catch (e) {
    toast.error('添加失败，请重试')
  } finally {
    addSaving.value = false
  }
}

async function doUpdate() {
  const f = editForm.value
  if (!f.word.trim() || !f.definition.trim()) {
    toast.warning('单词和释义不能为空')
    return
  }
  editSaving.value = true
  try {
    await vocabularyApi.updateVocabulary({
      id: f.id,
      word: f.word.trim(),
      definition: f.definition.trim(),
      partOfSpeech: f.partOfSpeech || null,
      phonetic: f.phonetic || null,
      exampleSentence: f.exampleSentence || null,
      exampleTranslation: f.exampleTranslation || null,
      langCode: f.langCode,
      level: f.level || null,
    })
    toast.success(`「${f.word}」已更新`)
    showEditModal.value = false
    loadData()
  } catch (e) {
    toast.error('更新失败，请重试')
  } finally {
    editSaving.value = false
  }
}

async function doDelete(id, word) {
  if (!confirm(`确定删除单词「${word}」吗？`)) return
  try {
    await vocabularyApi.deleteVocabulary(id)
    toast.success(`「${word}」已删除`)
    loadData()
  } catch (e) {
    toast.error('删除失败，请重试')
  }
}

const generatingExampleIds = ref(new Set())

async function generateExample(vocab) {
  if (generatingExampleIds.value.has(vocab.id)) return
  generatingExampleIds.value.add(vocab.id)
  try {
    const res = await fetch(`${API_BASE_URL}/vocabulary/generate-example?vocabId=${vocab.id}`, {
      method: 'POST'
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      vocab.exampleSentence = json.data.exampleSentence
      vocab.exampleTranslation = json.data.exampleTranslation
      toast.success('例句生成成功')
    } else {
      toast.error(json.message || '生成失败')
    }
  } catch (e) {
    toast.error('生成失败，请稍后重试')
  } finally {
    generatingExampleIds.value.delete(vocab.id)
  }
}

const examLevels = computed(() => getExamLevels(authStore.targetLanguage || 'en'))

onMounted(async () => {
  await languageStore.fetchLanguages()
  loadData()
  if (authStore.isLoggedIn && authStore.user) {
    await favoriteStore.fetchFavorites({ userId: authStore.user.id })
  }
})

watch(() => authStore.targetLanguage, () => {
  selectedLevel.value = null
  pageNo.value = 1
  loadData()
})

function loadData() {
  const params = { pageNo: pageNo.value, pageSize }
  if (authStore.targetLanguage) params.langCode = authStore.targetLanguage
  if (filter.value.partOfSpeech) params.partOfSpeech = filter.value.partOfSpeech
  if (filter.value.word) params.word = filter.value.word
  if (selectedLevel.value) params.level = selectedLevel.value
  vocabularyStore.fetchVocabularies(params)
}

function selectLevel(examLabel) {
  selectedLevel.value = selectedLevel.value === examLabel ? null : examLabel
  pageNo.value = 1
  loadData()
}

function onFilter(f) {
  filter.value = { partOfSpeech: f.partOfSpeech, word: f.word }
  pageNo.value = 1
  loadData()
}

function onPageChange(p) {
  pageNo.value = p
  loadData()
}

const toggleLoading = ref(new Set())
const speaking = ref(false)

const langMap = {
  en: 'en-US',
  ja: 'ja-JP',
  ko: 'ko-KR',
  fr: 'fr-FR',
  de: 'de-DE'
}

function speak(text, langCode = 'en') {
  if (speaking.value || !text) return
  
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = langMap[langCode] || 'en-US'
  utterance.rate = 0.85
  
  utterance.onstart = () => { speaking.value = true }
  utterance.onend = () => { speaking.value = false }
  utterance.onerror = () => { speaking.value = false }
  
  speechSynthesis.speak(utterance)
}

function speakWordDetail() {
  if (detailWord.value) {
    speak(detailWord.value.word, detailWord.value.langCode)
  }
}

function speakExampleDetail() {
  if (detailWord.value?.exampleSentence) {
    speak(detailWord.value.exampleSentence, detailWord.value.langCode)
  }
}

async function toggleFavorite(vocab) {
  if (!authStore.isLoggedIn) {
    toast.warning('请先登录后再收藏')
    return
  }
  if (toggleLoading.value.has(vocab.id)) return
  const isFav = favoriteStore.isFavorite(vocab.id)
  toggleLoading.value.add(vocab.id)
  try {
    if (isFav) {
      await favoriteStore.removeFavorite(vocab.id)
      toast.success('已取消收藏')
    } else {
      await favoriteStore.addFavorite(vocab.id, vocab.langCode)
      toast.success('收藏成功')
    }
  } catch (e) {
    toast.error(isFav ? '取消收藏失败，请重试' : '收藏失败，请重试')
  } finally {
    toggleLoading.value.delete(vocab.id)
  }
}
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="词汇库" tag="h1" />
      <p class="page-sub">浏览和管理你的词汇</p>
    </div>

    <VocabFilter
      :languages="languageStore.languages"
      :courses="courseStore.courses"
      @filter="onFilter"
    />

    <div class="toolbar-row">
      <div class="level-filter-bar">
        <span class="level-filter-label">等级:</span>
        <button class="level-filter-chip" :class="{ active: !selectedLevel }" @click="selectedLevel = null; pageNo = 1; loadData()">全部</button>
        <button v-for="lv in examLevels" :key="lv.examLabel" class="level-filter-chip" :class="{ active: selectedLevel === lv.examLabel }" @click="selectLevel(lv.examLabel)">{{ lv.examLabel }}</button>
      </div>
      <div class="toolbar-actions">
        <button class="add-btn-inline" @click="openAddModal">+ 添加词汇</button>
      </div>
    </div>

    <LoadingSpinner v-if="vocabularyStore.loading" />

    <div v-else-if="vocabularyStore.vocabularyList.length > 0" class="vocab-grid">
      <div class="vocab-card-wrapper" v-for="vocab in vocabularyStore.vocabularyList" :key="vocab.id">
        <VocabCard
          :word="vocab.word"
          :phonetic="vocab.phonetic"
          :definition="vocab.definition"
          :part-of-speech="vocab.partOfSpeech"
          :example-sentence="vocab.exampleSentence"
          :example-translation="vocab.exampleTranslation"
          :is-favorite="favoriteStore.isFavorite(vocab.id)"
          :lang-code="vocab.langCode || 'en'"
          @toggle-favorite="toggleFavorite(vocab)"
          @generate-example="generateExample(vocab)"
          @click.native="openDetailModal(vocab)"
        />
        <div class="card-actions">
          <button class="action-btn edit" @click.stop="openEditModal(vocab)">✎</button>
          <button class="action-btn delete" @click.stop="doDelete(vocab.id, vocab.word)">🗑</button>
        </div>
      </div>
    </div>

    <EmptyState
      v-else
      icon=""
      title="暂未找到词汇"
      description="尝试调整筛选条件或添加更多词汇"
    />

    <div v-if="vocabularyStore.total > pageSize" class="pagination-row">
      <Pagination
        :current-page="pageNo"
        :total="vocabularyStore.total"
        :page-size="pageSize"
        @page-change="onPageChange"
      />
    </div>

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showAddModal" class="modal-overlay" @click.self="showAddModal = false">
          <div class="modal-card">
            <div class="modal-header">
              <h3>添加新词汇</h3>
              <button class="modal-close" @click="showAddModal = false">✕</button>
            </div>
            <div class="modal-body">
              <div class="form-row-dual">
                <div class="form-group flex-2">
                  <label>单词 <span class="req">*</span></label>
                  <input v-model="addForm.word" placeholder="如: beautiful" @keyup.enter="doCreate" />
                </div>
                <div class="form-group flex-1">
                  <label>语言</label>
                  <select v-model="addForm.langCode">
                    <option v-for="l in languageStore.languages" :key="l.code" :value="l.code">{{ l.nameCn }}</option>
                  </select>
                </div>
              </div>
              <div class="form-row-dual">
                <div class="form-group flex-1">
                  <label>等级</label>
                  <select v-model="addForm.level">
                    <option value="">自动</option>
                    <option v-for="lv in examLevels" :key="lv.examLabel" :value="lv.examLabel">{{ lv.examLabel }}</option>
                  </select>
                </div>
                <div class="form-group flex-1">
                  <label>词性</label>
                  <select v-model="addForm.partOfSpeech">
                    <option value="">不指定</option>
                    <option value="noun">名词</option>
                    <option value="verb">动词</option>
                    <option value="adjective">形容词</option>
                    <option value="adverb">副词</option>
                    <option value="phrase">短语</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label>释义 <span class="req">*</span></label>
                <input v-model="addForm.definition" placeholder="如: 美丽的" />
              </div>
              <div class="form-group">
                <label>音标</label>
                <input v-model="addForm.phonetic" placeholder="如: /ˈbjuːtɪfl/" />
              </div>
              <div class="form-group">
                <label>例句</label>
                <textarea v-model="addForm.exampleSentence" placeholder="如: She has a beautiful smile." rows="2"></textarea>
              </div>
              <div class="form-group">
                <label>例句翻译</label>
                <textarea v-model="addForm.exampleTranslation" placeholder="如: 她有一个美丽的微笑。" rows="2"></textarea>
              </div>
            </div>
            <div class="modal-footer">
              <button class="cancel-btn" @click="showAddModal = false">取消</button>
              <button class="save-btn" :disabled="addSaving" @click="doCreate">
                {{ addSaving ? '添加中...' : '✓ 添加' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
          <div class="modal-card">
            <div class="modal-header">
              <h3>编辑词汇</h3>
              <button class="modal-close" @click="showEditModal = false">✕</button>
            </div>
            <div class="modal-body">
              <div class="form-row-dual">
                <div class="form-group flex-2">
                  <label>单词 <span class="req">*</span></label>
                  <input v-model="editForm.word" placeholder="如: beautiful" />
                </div>
                <div class="form-group flex-1">
                  <label>语言</label>
                  <select v-model="editForm.langCode">
                    <option v-for="l in languageStore.languages" :key="l.code" :value="l.code">{{ l.nameCn }}</option>
                  </select>
                </div>
              </div>
              <div class="form-row-dual">
                <div class="form-group flex-1">
                  <label>等级</label>
                  <select v-model="editForm.level">
                    <option value="">自动</option>
                    <option v-for="lv in examLevels" :key="lv.examLabel" :value="lv.examLabel">{{ lv.examLabel }}</option>
                  </select>
                </div>
                <div class="form-group flex-1">
                  <label>词性</label>
                  <select v-model="editForm.partOfSpeech">
                    <option value="">不指定</option>
                    <option value="noun">名词</option>
                    <option value="verb">动词</option>
                    <option value="adjective">形容词</option>
                    <option value="adverb">副词</option>
                    <option value="phrase">短语</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label>释义 <span class="req">*</span></label>
                <input v-model="editForm.definition" placeholder="如: 美丽的" />
              </div>
              <div class="form-group">
                <label>音标</label>
                <input v-model="editForm.phonetic" placeholder="如: /ˈbjuːtɪfl/" />
              </div>
              <div class="form-group">
                <label>例句</label>
                <textarea v-model="editForm.exampleSentence" placeholder="如: She has a beautiful smile." rows="2"></textarea>
              </div>
              <div class="form-group">
                <label>例句翻译</label>
                <textarea v-model="editForm.exampleTranslation" placeholder="如: 她有一个美丽的微笑。" rows="2"></textarea>
              </div>
            </div>
            <div class="modal-footer">
              <button class="cancel-btn" @click="showEditModal = false">取消</button>
              <button class="save-btn" :disabled="editSaving" @click="doUpdate">
                {{ editSaving ? '更新中...' : '✓ 更新' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDetailModal && detailWord" class="modal-overlay" @click.self="showDetailModal = false">
          <div class="detail-card">
            <div class="detail-header">
              <div class="detail-title-row">
                <button class="detail-speak-btn" @click="speakWordDetail" :class="{ speaking }">🔊</button>
                <h2 class="detail-word">{{ detailWord.word }}</h2>
                <button v-if="favoriteStore.isFavorite(detailWord.id)" class="fav-btn active" @click="toggleFavorite(detailWord)">★</button>
                <button v-else class="fav-btn" @click="toggleFavorite(detailWord)">☆</button>
              </div>
              <button class="modal-close" @click="showDetailModal = false">✕</button>
            </div>
            <div class="detail-body">
              <div v-if="detailWord.phonetic" class="detail-phonetic">{{ detailWord.phonetic }}</div>
              <div v-if="detailWord.partOfSpeech" class="detail-pos">{{ detailWord.partOfSpeech }}</div>
              <div class="detail-def">{{ detailWord.definition }}</div>
              <div v-if="detailWord.exampleSentence" class="detail-section">
                <h4>例句</h4>
                <div class="detail-example-row">
                  <p class="detail-example">{{ detailWord.exampleSentence }}</p>
                  <button class="detail-speak-btn-sm" @click="speakExampleDetail" :class="{ speaking }">🔊</button>
                </div>
                <p v-if="detailWord.exampleTranslation" class="detail-example-tr">{{ detailWord.exampleTranslation }}</p>
              </div>
              <div class="detail-meta">
                <span>语言: {{ detailWord.langCode }}</span>
                <span>等级: {{ detailWord.level || '未指定' }}</span>
              </div>
            </div>
            <div class="detail-footer">
              <button class="outline-btn" @click="openEditModal(detailWord)">编辑</button>
              <button class="delete-btn" @click="doDelete(detailWord.id, detailWord.word); showDetailModal = false">删除</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.page-header {
  text-align: center;
  padding: 24px 0 16px;
}

.page-header :deep(.letter-swap-title) {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-text);
  margin-bottom: 8px;
}

.page-sub {
  font-size: 15px;
  color: var(--color-text-secondary);
}

.vocab-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  padding: 8px 0 20px;
}

.vocab-card-wrapper {
  position: relative;
}

.card-actions {
  position: absolute;
  bottom: 10px;
  right: 10px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}

.vocab-card-wrapper:hover .card-actions {
  opacity: 1;
}

.action-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: none;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn.edit {
  background: rgba(124,157,181,0.1);
  color: #5a7d96;
}

.action-btn.delete {
  background: rgba(231,76,60,0.1);
  color: #e74c3c;
}

.level-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 0 12px; gap: 12px; flex-wrap: wrap;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.level-filter-label {
  font-size: 13px;
  color: var(--color-text-muted);
  font-weight: 600;
}

.level-filter-chip {
  padding: 5px 14px;
  border-radius: 20px;
  border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6);
  font-size: 12px;
  font-weight: 600;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.level-filter-chip:hover {
  border-color: #5a7d96;
  color: #5a7d96;
  background: rgba(90,125,150,0.06);
}

.level-filter-chip.active {
  border-color: #5a7d96;
  color: #5a7d96;
  background: rgba(90,125,150,0.1);
}

.add-btn-inline {
  padding: 8px 18px; border-radius: 10px; border: 1.5px solid #7c9db5;
  background: rgba(124,157,181,0.06); color: #5a7d96;
  font-size: 13px; font-weight: 600; cursor: pointer; white-space: nowrap;
  transition: all 0.25s;
}

.add-btn-inline:hover { background: #5a7d96; color: #fff; border-color: #5a7d96; }

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 16px;
}

/* 弹窗 */
.modal-overlay {
  position: fixed; inset: 0; z-index: 8000;
  background: rgba(0,0,0,0.2); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
}

.modal-card {
  background: #fff; border-radius: 16px; width: 440px; max-width: 95vw;
  box-shadow: 0 20px 60px rgba(0,0,0,0.12);
  max-height: 90vh; overflow-y: auto;
}

.detail-card {
  background: #fff; border-radius: 16px; width: 480px; max-width: 95vw;
  box-shadow: 0 20px 60px rgba(0,0,0,0.12);
}

.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px; border-bottom: 1px solid rgba(0,0,0,0.06);
}

.modal-header h3 { font-size: 17px; font-weight: 700; margin: 0; color: var(--color-text); }

.detail-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 22px; border-bottom: 1px solid rgba(0,0,0,0.06);
}

.detail-title-row {
  display: flex; align-items: center; gap: 12px;
}

.detail-speak-btn {
  width: 32px; height: 32px; border-radius: 50%;
  border: 1.5px solid #e0e0e0; background: rgba(255,255,255,0.8);
  font-size: 14px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.detail-speak-btn:hover { border-color: #7c9db5; background: rgba(124,157,181,0.08); }
.detail-speak-btn.speaking { border-color: #27ae60; background: rgba(39,174,96,0.1); animation: detailPulse 1s infinite; }

.detail-word {
  font-size: 28px; font-weight: 800; color: var(--color-text); margin: 0;
}

.detail-example-row {
  display: flex; align-items: flex-start; gap: 10px;
}

.detail-speak-btn-sm {
  width: 24px; height: 24px; border-radius: 50%;
  border: 1px solid #ddd; background: rgba(255,255,255,0.6);
  font-size: 11px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.2s;
}
.detail-speak-btn-sm:hover { border-color: #7c9db5; }
.detail-speak-btn-sm.speaking { border-color: #27ae60; background: rgba(39,174,96,0.1); animation: detailPulse 1s infinite; }

@keyframes detailPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.fav-btn {
  width: 36px; height: 36px; border-radius: 50%; border: none;
  background: rgba(0,0,0,0.05); color: #ddd; font-size: 20px; cursor: pointer;
  transition: all 0.2s;
}

.fav-btn.active {
  background: rgba(240,151,92,0.1); color: #f0975c;
}

.modal-close {
  width: 30px; height: 30px; border-radius: 50%; border: none;
  background: rgba(0,0,0,0.05); color: #666; font-size: 16px; cursor: pointer;
}

.modal-close:hover { background: rgba(0,0,0,0.1); }

.modal-body { padding: 18px 22px; }

.detail-body { padding: 22px; }

.detail-phonetic {
  font-size: 16px; color: #999; margin-bottom: 8px;
}

.detail-pos {
  display: inline-block;
  padding: 4px 12px; border-radius: 8px;
  background: rgba(124,92,191,0.08); color: #7c5cbf;
  font-size: 13px; font-weight: 600;
  margin-bottom: 16px;
}

.detail-def {
  font-size: 20px; color: var(--color-text);
  line-height: 1.6;
  margin-bottom: 20px;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section h4 {
  font-size: 14px; font-weight: 700; color: #888; margin-bottom: 8px;
}

.detail-example {
  font-size: 15px; color: #444; font-style: italic;
  margin-bottom: 4px;
}

.detail-example-tr {
  font-size: 13px; color: #999;
}

.detail-meta {
  display: flex; gap: 16px;
  font-size: 12px; color: #bbb;
  padding-top: 12px;
  border-top: 1px solid rgba(0,0,0,0.06);
}

.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 600; color: #666; margin-bottom: 4px; }
.form-group .req { color: #e74c3c; }
.form-group input, .form-group select, .form-group textarea {
  width: 100%; padding: 10px 14px; border-radius: 10px; border: 1.5px solid #e0e0e0;
  background: #fafafa; font-size: 14px; color: var(--color-text); outline: none;
  font-family: var(--font-body);
}
.form-group textarea { resize: vertical; min-height: 60px; }
.form-group input:focus, .form-group select:focus, .form-group textarea:focus { border-color: #7c9db5; box-shadow: 0 0 0 3px rgba(124,157,181,0.08); }
.form-row-dual { display: flex; gap: 12px; }
.flex-2 { flex: 2; }
.flex-1 { flex: 1; }

.modal-footer {
  display: flex; gap: 10px; justify-content: flex-end;
  padding: 14px 22px; border-top: 1px solid rgba(0,0,0,0.06);
}

.detail-footer {
  display: flex; gap: 12px; justify-content: flex-end;
  padding: 18px 22px; border-top: 1px solid rgba(0,0,0,0.06);
}

.cancel-btn {
  padding: 9px 20px; border-radius: 10px; border: 1.5px solid #ddd;
  background: #fff; color: #666; font-size: 14px; font-weight: 500; cursor: pointer;
}

.save-btn {
  padding: 9px 24px; border-radius: 10px; border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  font-size: 14px; font-weight: 600; cursor: pointer;
}

.save-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.delete-btn {
  padding: 9px 24px; border-radius: 10px; border: none;
  background: linear-gradient(135deg, #e74c3c, #c0392b); color: #fff;
  font-size: 14px; font-weight: 600; cursor: pointer;
}

.outline-btn {
  padding: 9px 24px; border-radius: 10px; border: 1.5px solid #7c9db5;
  background: #fff; color: #5a7d96; font-size: 14px; font-weight: 600; cursor: pointer;
}

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-active .modal-card, .modal-leave-active .modal-card { transition: transform 0.2s; }
.modal-enter-active .detail-card, .modal-leave-active .detail-card { transition: transform 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-from .modal-card { transform: translateY(20px) scale(0.96); }
.modal-leave-to .modal-card { transform: translateY(10px) scale(0.98); }
.modal-enter-from .detail-card { transform: translateY(20px) scale(0.96); }
.modal-leave-to .detail-card { transform: translateY(10px) scale(0.98); }
</style>