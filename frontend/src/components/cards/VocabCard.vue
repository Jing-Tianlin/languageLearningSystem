<script setup>
import { ref } from 'vue'

const props = defineProps({
  word: { type: String, default: '' },
  phonetic: { type: String, default: '' },
  definition: { type: String, default: '' },
  partOfSpeech: { type: String, default: '' },
  exampleSentence: { type: String, default: '' },
  exampleTranslation: { type: String, default: '' },
  isFavorite: { type: Boolean, default: false },
  langCode: { type: String, default: 'en' },
})

const emit = defineEmits(['toggle-favorite', 'generate-example', 'select'])

const speaking = ref(false)

const langMap = {
  en: 'en-US',
  ja: 'ja-JP',
  ko: 'ko-KR',
  fr: 'fr-FR',
  de: 'de-DE'
}

function speak(text) {
  if (speaking.value || !text) return
  
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = langMap[props.langCode] || 'en-US'
  utterance.rate = 0.85
  
  utterance.onstart = () => { speaking.value = true }
  utterance.onend = () => { speaking.value = false }
  utterance.onerror = () => { speaking.value = false }
  
  speechSynthesis.speak(utterance)
}

function speakWord() {
  speak(props.word)
}

function speakExample() {
  speak(props.exampleSentence)
}

function stopSpeaking() {
  speechSynthesis.cancel()
  speaking.value = false
}
</script>

<template>
  <div class="vocab-card" @click="emit('select')">
    <!-- 收藏按钮：固定在卡片右上角 -->
    <button class="vocab-fav-btn btn btn-icon btn-ghost" :class="{ active: isFavorite }" @click.stop="$emit('toggle-favorite')">
      {{ isFavorite ? '★' : '☆' }}
    </button>

    <div class="card-header">
      <div class="word-section">
        <span class="vocab-word">{{ word }}</span>
        <button class="btn btn-icon btn-ghost" @click.stop="speakWord" :class="{ speaking }">
          <span v-if="speaking" class="sound-wave">♪</span>
          <span v-else class="icon-svg speaker" />
        </button>
      </div>
      <div class="meta-row">
        <span v-if="phonetic" class="vocab-phonetic">/{{ phonetic }}/</span>
        <span v-if="partOfSpeech" class="vocab-pos">{{ partOfSpeech }}</span>
      </div>
    </div>
    
    <div class="card-body">
      <p class="vocab-definition">{{ definition }}</p>
      
      <div v-if="exampleSentence" class="example-block">
        <div class="example-header">
          <span class="example-label">例句</span>
          <button class="btn btn-icon btn-sm btn-ghost" @click.stop="speakExample" :class="{ speaking }"><span class="icon-svg speaker" /></button>
        </div>
        <p class="vocab-example">{{ exampleSentence }}</p>
        <p v-if="exampleTranslation" class="vocab-example-tr">{{ exampleTranslation }}</p>
      </div>
      
      <div v-else class="no-example">
        <span class="no-example-icon icon-svg notebook" />
        <span class="no-example-text">暂无例句</span>
        <button class="btn btn-primary btn-sm" @click.stop="emit('generate-example')">
          <span class="icon-svg sparkles" /> AI生成
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vocab-card {
  position: relative;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0.7) 100%);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  padding: 20px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 12px rgba(124, 92, 191, 0.06);
}
.vocab-card:hover {
  transform: translateY(-3px);
  border-color: rgba(124, 157, 181, 0.3);
  box-shadow: 0 8px 28px rgba(124, 157, 181, 0.15);
}

.card-header {
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(124, 157, 181, 0.1);
}

.word-section {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  padding-right: 36px; /* 为右上角收藏按钮留出空间 */
}

.vocab-word {
  font-size: 24px;
  font-weight: 700;
  color: #2c3e50;
  font-family: var(--font-heading, 'Georgia', serif);
  line-height: 1.2;
}

.vocab-speak-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #7c9db5 0%, #5a7d96 100%);
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease;
  flex-shrink: 0;
  color: white;
  box-shadow: 0 2px 8px rgba(124, 157, 181, 0.3);
}
.vocab-speak-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 4px 12px rgba(124, 157, 181, 0.4);
}
.vocab-speak-btn.speaking {
  background: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%);
  animation: vocabPulse 1s ease-in-out infinite;
  box-shadow: 0 2px 12px rgba(39, 174, 96, 0.4);
}
.sound-wave {
  font-size: 14px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.vocab-phonetic {
  font-size: 13px;
  color: #7f8c8d;
  font-style: italic;
}

.vocab-pos {
  font-size: 11px;
  background: linear-gradient(135deg, rgba(124, 157, 181, 0.15) 0%, rgba(90, 125, 150, 0.15) 100%);
  padding: 3px 10px;
  border-radius: 6px;
  color: #5a7d96;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-body {
  flex: 1;
}

.vocab-definition {
  font-size: 15px;
  color: #34495e;
  margin: 0 0 14px 0;
  line-height: 1.6;
  font-weight: 500;
}

.example-block {
  background: linear-gradient(135deg, rgba(124, 157, 181, 0.06) 0%, rgba(90, 125, 150, 0.06) 100%);
  border-radius: 10px;
  padding: 12px 14px;
  border-left: 3px solid #7c9db5;
}

.example-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.example-label {
  font-size: 11px;
  font-weight: 600;
  color: #5a7d96;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.vocab-example {
  font-size: 13px;
  color: #34495e;
  margin: 0 0 4px 0;
  line-height: 1.5;
  font-style: italic;
}

.vocab-speak-btn-sm {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: rgba(124, 157, 181, 0.15);
  font-size: 9px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
  color: #5a7d96;
}
.vocab-speak-btn-sm:hover {
  background: rgba(124, 157, 181, 0.25);
  transform: scale(1.1);
}
.vocab-speak-btn-sm.speaking {
  background: rgba(39, 174, 96, 0.2);
  color: #27ae60;
  animation: vocabPulse 1s ease-in-out infinite;
}

.vocab-example-tr {
  font-size: 12px;
  color: #7f8c8d;
  margin: 0;
  line-height: 1.4;
}

.no-example {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(127, 140, 141, 0.06);
  border-radius: 10px;
  border: 1px dashed rgba(127, 140, 141, 0.2);
  flex-wrap: wrap;
}

.no-example-icon {
  font-size: 14px;
}

.no-example-text {
  font-size: 12px;
  color: #95a5a6;
}

.gen-example-btn {
  padding: 4px 10px;
  border-radius: 6px;
  border: none;
  background: linear-gradient(135deg, #7c9db5 0%, #5a7d96 100%);
  color: white;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.gen-example-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(124, 157, 181, 0.4);
}

.gen-example-btn:active {
  transform: translateY(0);
}

.vocab-fav-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  font-size: 20px;
  line-height: 1;
  color: var(--color-text-muted);
  background: transparent;
  border-color: transparent;
  transition: all 0.25s ease;
}
.vocab-fav-btn:hover:not(:disabled) {
  transform: scale(1.18);
  color: var(--color-gold, #b07c4f);
  background: transparent;
  border-color: transparent;
}
.vocab-fav-btn.active {
  color: var(--color-gold, #b07c4f);
}

@keyframes vocabPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.8; }
}
</style>
