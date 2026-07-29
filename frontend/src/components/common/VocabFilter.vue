<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/stores/language'

defineProps({
  languages: { type: Array, default: () => [] },
  courses: { type: Array, default: () => [] },
})

const authStore = useAuthStore()
const languageStore = useLanguageStore()
const emit = defineEmits(['filter'])
const pos = ref('')
const word = ref('')

const langCode = computed(() => authStore.targetLanguage || '')
const langName = computed(() => {
  const l = languageStore.languages.find(l => l.code === langCode.value)
  return l ? l.nameCn : '全部语言'
})

const posCategories = [
  { code: '', name: '全部词性' },
  { code: 'noun', name: '名词' },
  { code: 'verb', name: '动词' },
  { code: 'adjective', name: '形容词' },
  { code: 'adverb', name: '副词' },
  { code: 'phrase', name: '短语' },
  { code: 'greeting', name: '问候语' },
  { code: 'interjection', name: '感叹词' },
]

function apply() {
  emit('filter', { langCode: langCode.value, partOfSpeech: pos.value, word: word.value })
  word.value = ''
}
function reset() {
  pos.value = ''
  word.value = ''
  emit('filter', { langCode: langCode.value, partOfSpeech: '', word: '' })
}
</script>

<template>
  <div class="vocab-filter">
    <!-- 当前语言标签（只读） -->
    <span class="lang-label">{{ langName }}</span>

    <!-- 词性选择 -->
    <div class="chip-group">
      <button
        v-for="p in posCategories" :key="p.code"
        class="chip" :class="{ active: pos === p.code }"
        @click="pos = p.code; apply()"
      >{{ p.name }}</button>
    </div>

    <!-- 搜索 -->
    <div class="search-row">
      <input v-model="word" placeholder="搜索单词..." @keyup.enter="apply" />
      <button class="search-btn" @click="apply">搜索</button>
      <button class="reset-btn" @click="reset">重置</button>
    </div>
  </div>
</template>

<style scoped>
.vocab-filter {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  margin-bottom: 16px;
}

.lang-label {
  padding: 4px 14px; border-radius: 10px;
  background: rgba(90,125,150,0.1); color: #5a7d96;
  font-size: 13px; font-weight: 700;
}

.chip-group {
  display: flex; gap: 6px; flex-wrap: wrap; justify-content: center;
}
.chip {
  padding: 5px 12px; border-radius: 16px;
  border: 1.5px solid #ddd; background: rgba(255,255,255,0.6);
  font-size: 12px; font-weight: 600; color: #666; cursor: pointer;
  transition: all 0.25s; white-space: nowrap;
}
.chip:hover { border-color: #aaa; }
.chip.active { border-color: #5a7d96; color: #fff; background: #5a7d96; }

.search-row {
  display: flex; gap: 8px; align-items: center;
}
.search-row input {
  padding: 8px 16px; border-radius: 22px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); color: var(--color-text);
  font-size: 14px; outline: none; width: 220px;
  transition: border-color 0.3s, box-shadow 0.3s;
}
.search-row input:focus { border-color: #7c9db5; box-shadow: 0 0 0 3px rgba(124,157,181,0.08); }
.search-row input::placeholder { color: #bbb; }

.search-btn {
  padding: 8px 18px; border-radius: 22px; border: none;
  background: #5a7d96; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
}
.search-btn:hover { background: #4a6d86; }

.reset-btn {
  padding: 8px 14px; border-radius: 22px; border: 1.5px solid #ddd;
  background: transparent; color: #999; font-size: 13px; font-weight: 500; cursor: pointer;
}
.reset-btn:hover { background: rgba(0,0,0,0.04); color: #666; }
</style>
