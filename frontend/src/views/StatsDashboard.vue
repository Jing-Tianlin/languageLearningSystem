<script setup>
/**
 * StatsDashboard.vue — 学习分析 (算法强化版)
 *
 * 数据来源:
 *  - GET /stats/overview  → 总词汇/已掌握/平均熟悉度/平均犹豫/复习总量/巡检次数
 *  - GET /stats/weak-points → 7维语法雷达图
 *  - GET /stats/by-language → 按语言掌握分布
 *  - GET /stats/trend → 最近7天复习趋势
 */
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { RadarChart, BarChart, PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([RadarChart, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import GamificationPanel from '@/components/gamification/GamificationPanel.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { API_BASE_URL } from '@/config'

const authStore = useAuthStore()
const loading = ref(true)

const radarOption = ref({})
const trendOption = ref({})
const pieOption = ref({})

const overview = ref({ totalWords: 0, masteredWords: 0, masteryRate: 0, avgFamiliarity: 0, avgHesitationMs: 0, totalReviews: 0, inspections: 0, writingCount: 0, readingCount: 0, readingAvgAccuracy: 0 })

const BASE = API_BASE_URL

onMounted(async () => {
  const userId = localStorage.getItem('userId')
  if (!userId) { loading.value = false; return }

  try {
    // 1. 综合掌握度 (雷达图)
    const r1 = await fetch(`${BASE}/stats/weak-points?userId=${userId}`)
    const j1 = await r1.json()
    if (j1.code === 200 && j1.data) {
      const data = j1.data
      const indicators = Object.keys(data).map((k) => ({ name: getLabel(k), max: 100 }))
      const values = Object.values(data).map((v) => Math.round(v * 100))
      // 检查是否有任何维度数据 > 0
      const hasAnyData = values.some(v => v > 0)
      const areaColor = hasAnyData ? 'rgba(90,125,150,0.2)' : 'rgba(200,200,200,0.08)'
      const lineColor = hasAnyData ? '#5a7d96' : '#ccc'
      radarOption.value = {
        tooltip: {}, radar: { indicator: indicators, center: ['50%','55%'], radius:'65%', axisName:{fontSize:12,color:'#666'} },
        series: [{ type:'radar', data: [{ value:values, name:'掌握度', areaStyle:{color:areaColor} }], lineStyle:{color:lineColor,width:2}, itemStyle:{color:lineColor} }]
      }
    }

    // 2. 总体指标
    const r2 = await fetch(`${BASE}/stats/overview?userId=${userId}`)
    const j2 = await r2.json()
    if (j2.code === 200 && j2.data) overview.value = j2.data

    // 3. 按语言分布 (饼图)
    const r3 = await fetch(`${BASE}/stats/by-language?userId=${userId}`)
    const j3 = await r3.json()
    if (j3.code === 200 && j3.data?.languages) {
      const langs = j3.data.languages
      pieOption.value = {
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: ['45%','75%'], center: ['50%','55%'],
          data: Object.entries(langs).map(([k,v]) => ({ name: k.toUpperCase(), value: v.total })),
          label: { formatter: '{b}\n{d}%' },
        }]
      }
    }

    // 4. 趋势（堆叠柱状图 + 均值参考线）
    const r4 = await fetch(`${BASE}/stats/trend?userId=${userId}`)
    const j4 = await r4.json()
    if (j4.code === 200 && j4.data?.trend) {
      const t = j4.data.trend
      const days = t.days || []
      const vocab = t.vocab || []
      const writing = t.writing || []
      const reading = t.reading || []
      const avg = t.avgDaily || 0
      trendOption.value = {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: function(params) {
            let html = `<b>${params[0].axisValue}</b><br/>`
            let total = 0
            params.forEach(p => {
              if (p.seriesName !== '日均参考线') {
                html += `${p.marker} ${p.seriesName}: <b>${p.value}</b><br/>`
                total += p.value
              }
            })
            html += `<hr style="margin:4px 0"/>合计: <b>${total}</b> 次`
            if (avg > 0) {
              const diff = total - avg
              html += `<br/>较均值 ${diff >= 0 ? '+' : ''}${diff}`
            }
            return html
          },
        },
        legend: {
          data: ['词汇复习', '写作', '阅读'],
          bottom: 0,
          itemGap: 30,
          textStyle: { fontSize: 12, color: '#888' },
        },
        grid: { left: '3%', right: '10%', top: 20, bottom: 50 },
        xAxis: {
          type: 'category',
          data: days,
          axisLine: { lineStyle: { color: '#ddd' } },
          axisLabel: { color: '#999', fontSize: 11 },
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
          axisLabel: { color: '#999', fontSize: 11 },
        },
        series: [
          {
            name: '词汇复习', type: 'bar', stack: 'total',
            data: vocab, barWidth: 36,
            itemStyle: { color: '#7c9db5', borderRadius: [0, 0, 0, 0] },
            emphasis: { itemStyle: { color: '#5a7d96' } },
          },
          {
            name: '写作', type: 'bar', stack: 'total',
            data: writing, barWidth: 36,
            itemStyle: { color: '#8ab5a0', borderRadius: [0, 0, 0, 0] },
            emphasis: { itemStyle: { color: '#6a9a80' } },
          },
          {
            name: '阅读', type: 'bar', stack: 'total',
            data: reading, barWidth: 36,
            itemStyle: { color: '#d4a76a', borderRadius: [6, 6, 0, 0] },
            emphasis: { itemStyle: { color: '#c09050' } },
          },
          {
            name: '日均参考线', type: 'line',
          data: days.map(() => avg),
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#e88', width: 1.5, type: 'dashed' },
          label: {
            show: true, position: 'end',
            formatter: `日均 ${avg}`,
            color: '#c55', fontSize: 10,
          },
          },
        ],
      }
    } else {
    }
  } catch(e) { console.error('[趋势] 异常:', e) }
  finally { loading.value = false }
})

function getLabel(k) { const m={spelling:'拼写',preposition:'介词',tense:'时态',article:'冠词',word_order:'语序',conjugation:'变位',vocabulary:'词汇'}; return m[k]||k }
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="学习分析" tag="h1" />
      <p class="page-sub">基于学习数据的智能分析</p>
    </div>

    <LoadingSpinner v-if="loading" />

    <template v-else>
      <!-- 游戏化激励 -->
      <GamificationPanel />

      <!-- 概览 -->
      <div class="overview-grid">
        <div class="stat-card" v-for="s in [
          {icon:'vocab',val:overview.totalWords,lbl:'学习词汇'},
          {icon:'star',val:overview.masteredWords,lbl:'已掌握'},
          {icon:'chart',val:overview.masteryRate+'%',lbl:'掌握率'},
          {icon:'brain',val:overview.avgFamiliarity,lbl:'平均熟悉度'},
          {icon:'clock',val:overview.avgHesitationMs+'ms',lbl:'平均犹豫'},
          {icon:'link',val:overview.totalReviews,lbl:'总复习次数'},
          {icon:'search',val:overview.inspections,lbl:'巡检次数'},
          {icon:'pen',val:overview.writingCount,lbl:'写作篇数'},
          {icon:'book',val:overview.readingCount,lbl:'阅读篇数'},
          {icon:'chart',val:overview.readingAvgAccuracy+'%',lbl:'阅读正确率'},
        ]" :key="s.lbl">
          <span class="stat-icon icon-svg" :class="s.icon"></span>
          <div><div class="stat-val">{{ s.val }}</div><div class="stat-lbl">{{ s.lbl }}</div></div>
        </div>
      </div>

      <!-- 雷达图 + 饼图 并排 -->
      <div class="row-charts">
        <div class="chart-card half">
          <h3 class="chart-title">语法维度雷达图</h3>
          <VChart v-if="radarOption.series" :option="radarOption" style="height:320px" autoresize />
          <p v-else class="no-data">暂无语法维度数据</p>
        </div>
        <div class="chart-card half">
          <h3 class="chart-title">按语言掌握分布</h3>
          <VChart v-if="pieOption.series" :option="pieOption" style="height:320px" autoresize />
          <p v-else class="no-data">暂无语言分布数据</p>
        </div>
      </div>

      <!-- 趋势 -->
      <div class="chart-card">
        <h3 class="chart-title">最近7天学习活动（按类型分级）</h3>
        <VChart v-if="trendOption.series" :option="trendOption" style="height:300px" autoresize />
        <p v-else class="no-data">暂无趋势数据</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 900px; margin: 0 auto; padding-bottom: 40px; }
.page-header { text-align: center; padding: 24px 0 10px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--color-text-muted); }

.overview-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 10px; margin: 20px 0; }
.stat-card { display: flex; align-items: center; gap: 10px; background: rgba(255,255,255,0.72); backdrop-filter: blur(12px); border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-md); padding: 14px 16px; }
.stat-icon { font-size: 22px; }
.stat-val { font-size: 15px; font-weight: 700; color: var(--color-text); }
.stat-lbl { font-size: 11px; color: var(--color-text-muted); }

.row-charts { display: flex; gap: 16px; flex-wrap: wrap; margin-top: 16px; }
.chart-card { background: rgba(255,255,255,0.78); backdrop-filter: blur(14px); border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg); padding: 22px 24px; margin-top: 16px; }
.chart-card.half { flex: 1; min-width: 300px; }
.chart-title { font-size: 15px; font-weight: 700; color: var(--color-text); margin-bottom: 10px; }
.no-data { text-align: center; color: #bbb; padding: 40px 0; font-size: 13px; }
.loading { text-align: center; color: var(--color-text-muted); padding: 60px 0; }
</style>
