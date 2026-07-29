/**
 * examLevels.js — 统一考试等级配置
 *
 * 通用等级 0-5，映射到各语言的实际考试等级
 * 用于前端展示和后端筛选
 */

// 通用等级定义
export const LEVELS = [
  { value: 0, label: '入门', desc: '零基础起步' },
  { value: 1, label: '初级', desc: '掌握基础语法和词汇' },
  { value: 2, label: '中级', desc: '能进行日常交流' },
  { value: 3, label: '中高级', desc: '能处理复杂话题' },
  { value: 4, label: '高级', desc: '接近母语水平' },
  { value: 5, label: '精通', desc: '专业/学术级别' },
]

// 各语言对应的考试等级
export const EXAM_LEVELS = {
  en: {
    name: '英语',
    exam: '学段/考试',
    levels: [
      { value: 0, examLabel: '小学', examName: '小学英语', desc: '基础词汇与简单句型' },
      { value: 1, examLabel: '初中', examName: '初中英语', desc: '中考水平，日常交流' },
      { value: 2, examLabel: '高中', examName: '高中英语', desc: '高考水平，基础读写' },
      { value: 3, examLabel: 'CET4', examName: '大学四级', desc: '大学英语四级水平' },
      { value: 4, examLabel: 'CET6', examName: '大学六级', desc: '大学英语六级水平' },
      { value: 5, examLabel: '专业人士', examName: '专业/母语', desc: '学术和专业领域运用' },
    ],
  },
  ja: {
    name: '日语',
    exam: 'JLPT',
    levels: [
      { value: 0, examLabel: 'N5', examName: '入门级', desc: '掌握平假名、基础语法' },
      { value: 1, examLabel: 'N4', examName: '初级', desc: '能理解日常简单会话' },
      { value: 2, examLabel: 'N3', examName: '中级', desc: '能理解日常大部分场景' },
      { value: 3, examLabel: 'N2', examName: '中高级', desc: '能理解广泛场景日语' },
      { value: 4, examLabel: 'N1', examName: '高级', desc: '能理解高难度日语' },
      { value: 5, examLabel: 'N1+', examName: '精通', desc: '接近母语水平' },
    ],
  },
  ko: {
    name: '韩语',
    exam: 'TOPIK',
    levels: [
      { value: 0, examLabel: '1级', examName: '入门级', desc: 'TOPIK I / 基础韩语' },
      { value: 1, examLabel: '2级', examName: '初级', desc: 'TOPIK I / 简单日常' },
      { value: 2, examLabel: '3级', examName: '中级', desc: 'TOPIK II / 基本社交' },
      { value: 3, examLabel: '4级', examName: '中高级', desc: 'TOPIK II / 社会话题' },
      { value: 4, examLabel: '5级', examName: '高级', desc: 'TOPIK II / 专业领域' },
      { value: 5, examLabel: '6级', examName: '精通', desc: 'TOPIK II / 研究级别' },
    ],
  },
  fr: {
    name: '法语',
    exam: 'DELF/DALF',
    levels: [
      { value: 0, examLabel: 'A1', examName: '入门级', desc: 'DELF A1 / 基础交流' },
      { value: 1, examLabel: 'A2', examName: '初级', desc: 'DELF A2 / 简单交流' },
      { value: 2, examLabel: 'B1', examName: '中级', desc: 'DELF B1 / 独立使用' },
      { value: 3, examLabel: 'B2', examName: '中高级', desc: 'DELF B2 / 流利交流' },
      { value: 4, examLabel: 'C1', examName: '高级', desc: 'DALF C1 / 高级运用' },
      { value: 5, examLabel: 'C2', examName: '精通', desc: 'DALF C2 / 精通水平' },
    ],
  },
  de: {
    name: '德语',
    exam: 'Goethe-Zertifikat',
    levels: [
      { value: 0, examLabel: 'A1', examName: '入门级', desc: 'A1 / 基础日常' },
      { value: 1, examLabel: 'A2', examName: '初级', desc: 'A2 / 简单日常' },
      { value: 2, examLabel: 'B1', examName: '中级', desc: 'B1 / 独立使用' },
      { value: 3, examLabel: 'B2', examName: '中高级', desc: 'B2 / 流利交流' },
      { value: 4, examLabel: 'C1', examName: '高级', desc: 'C1 / 高级运用' },
      { value: 5, examLabel: 'C2', examName: '精通', desc: 'C2 / 精通水平' },
    ],
  },
}

/**
 * 获取某语言的等级列表
 */
export function getExamLevels(langCode) {
  return EXAM_LEVELS[langCode]?.levels || EXAM_LEVELS.en.levels
}

/**
 * 获取某语言的考试名称
 */
export function getExamName(langCode) {
  return EXAM_LEVELS[langCode]?.exam || 'CEFR'
}

/**
 * 获取某等级的详细信息
 */
export function getLevelInfo(langCode, levelValue) {
  const levels = getExamLevels(langCode)
  if (levelValue === null || levelValue === undefined || levelValue === -1) return levels[0]
  return levels.find(l => l.value === levelValue) || levels[0]
}

/**
 * 获取等级显示标签（如 "B1 · 中级"）
 */
export function getLevelLabel(langCode, levelValue) {
  if (levelValue === null || levelValue === undefined || levelValue === -1) return '全部等级'
  const info = getLevelInfo(langCode, levelValue)
  return `${info.examLabel} · ${info.examName}`
}

/**
 * 获取等级简短标签（如 "B1"）
 */
export function getLevelShortLabel(langCode, levelValue) {
  if (levelValue === null || levelValue === undefined || levelValue === -1) return '全部'
  const info = getLevelInfo(langCode, levelValue)
  return info.examLabel
}

/**
 * 旧等级到新等级的映射
 * - 阅读文章: level_num 1-6 → 0-5
 * - 语法练习: level 0-2 → 0-2（需要扩展）
 * - 课程: Beginner=1, Elementary=1, Intermediate=2, Advanced=3
 * - 长难句: Intermediate=2-3, Advanced=3-4
 */
export const LEGACY_MAP = {
  reading: {
    'Beginner': 0,
    'Elementary': 1,
    'Pre-Intermediate': 2,
    'Intermediate': 3,
    'Upper-Intermediate': 4,
    'Advanced': 5,
  },
  grammar: {
    0: 0, // Beginner → 入门
    1: 2, // Intermediate → 中级
    2: 4, // Advanced → 高级
  },
  course: {
    'Beginner': 0,
    'Elementary': 1,
    'Intermediate': 3,
    'Advanced': 5,
  },
  sentence: {
    'Intermediate': 3,
    'Advanced': 5,
  },
}
