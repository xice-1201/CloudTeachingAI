<template>
  <section class="ability-portrait-panel" v-loading="loading">
    <div class="portrait-header">
      <div>
        <div class="portrait-title">个人能力画像</div>
        <div class="portrait-subtitle">基于能力测试和学习进度生成的知识点掌握情况</div>
      </div>
      <el-button type="primary" @click="router.push('/learning/ability-test')">能力测试</el-button>
    </div>

    <el-empty
      v-if="!loading && !hasAbilityData"
      description="还没有可用于分析的能力数据，请先完成一次能力测试。"
    >
      <el-button type="primary" @click="router.push('/learning/ability-test')">前往能力测试</el-button>
    </el-empty>

    <template v-else>
      <section class="portrait-summary">
        <div class="summary-item">
          <div class="summary-label">已分析知识点</div>
          <div class="summary-value">{{ abilityMap.length }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">平均掌握度</div>
          <div class="summary-value">{{ Math.round(averageMastery * 100) }}%</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">建议补强</div>
          <div class="summary-text">{{ weakestNamesText }}</div>
        </div>
      </section>

      <section class="legend-row">
        <span v-for="item in masteryLegend" :key="item.label" class="legend-item">
          <span class="legend-dot" :style="{ backgroundColor: item.color }" />
          {{ item.label }}
        </span>
      </section>

      <div ref="chartEl" class="portrait-chart" />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import type { AbilityMap, KnowledgePointNode } from '@/types'

type PortraitNode = {
  id: number
  parentId?: number | null
  name: string
  path: string
  nodeType: KnowledgePointNode['nodeType']
  masteryLevel: number
  confidence: number
  resourceCount: number
  source: AbilityMap['source'] | 'NO_DATA'
}

const router = useRouter()
const chartEl = ref<HTMLElement>()
const loading = ref(false)
const knowledgePoints = ref<KnowledgePointNode[]>([])
const abilityMap = ref<AbilityMap[]>([])
let chart: echarts.ECharts | null = null

const masteryLegend = [
  { label: '无数据 0%', color: '#c0c4cc' },
  { label: '待补强', color: '#f56c6c' },
  { label: '发展中', color: '#e6a23c' },
  { label: '掌握较好', color: '#67c23a' },
]

const hasAbilityData = computed(() => abilityMap.value.length > 0)

const portraitNodes = computed<PortraitNode[]>(() => {
  const abilityByKnowledgePoint = new Map(abilityMap.value.map((item) => [item.knowledgePointId, item]))
  const nodes: PortraitNode[] = []

  const walk = (items: KnowledgePointNode[]) => {
    items.forEach((item) => {
      if (item.active !== false) {
        const ability = abilityByKnowledgePoint.get(item.id)
        nodes.push({
          id: item.id,
          parentId: item.parentId,
          name: item.name,
          path: item.path,
          nodeType: item.nodeType,
          masteryLevel: ability?.masteryLevel ?? 0,
          confidence: ability?.confidence ?? 0,
          resourceCount: ability?.resourceCount ?? 0,
          source: ability?.source ?? 'NO_DATA',
        })
      }
      if (item.children?.length) walk(item.children)
    })
  }

  walk(knowledgePoints.value)
  return nodes
})

const averageMastery = computed(() => {
  if (abilityMap.value.length === 0) return 0
  return abilityMap.value.reduce((sum, item) => sum + item.masteryLevel, 0) / abilityMap.value.length
})

const weakestNamesText = computed(() => {
  if (abilityMap.value.length === 0) return '暂无'
  return [...abilityMap.value]
    .sort((left, right) => left.masteryLevel - right.masteryLevel)
    .slice(0, 3)
    .map((item) => item.knowledgePointName)
    .join('、')
})

function masteryColor(level: number, hasData: boolean) {
  if (!hasData) return '#c0c4cc'
  if (level >= 0.75) return '#67c23a'
  if (level >= 0.45) return '#e6a23c'
  return '#f56c6c'
}

function nodeCategory(type: KnowledgePointNode['nodeType']) {
  return { SUBJECT: 0, DOMAIN: 1, POINT: 2 }[type] ?? 2
}

function sourceLabel(source: PortraitNode['source']) {
  return {
    TEST: '能力测试',
    TEST_AND_PROGRESS: '测试 + 学习进度',
    LEARNING_PROGRESS: '学习进度',
    COURSE_TAGS: '课程资源标签',
    NO_DATA: '暂无数据',
  }[source] ?? source
}

function renderChart() {
  if (!chartEl.value || !hasAbilityData.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const nodeIds = new Set(portraitNodes.value.map((item) => item.id))
  const nodes = portraitNodes.value.map((item) => {
    const hasData = item.source !== 'NO_DATA'
    return {
      id: String(item.id),
      name: item.name,
      value: item.masteryLevel,
      category: nodeCategory(item.nodeType),
      symbolSize: item.nodeType === 'SUBJECT' ? 42 : item.nodeType === 'DOMAIN' ? 34 : 28,
      itemStyle: {
        color: masteryColor(item.masteryLevel, hasData),
        opacity: hasData ? 1 : 0.56,
      },
      label: {
        show: true,
        formatter: `${item.name}\n${Math.round(item.masteryLevel * 100)}%`,
      },
      tooltip: {
        formatter: [
          `<strong>${item.name}</strong>`,
          `路径：${item.path}`,
          `掌握度：${Math.round(item.masteryLevel * 100)}%`,
          `置信度：${Math.round(item.confidence * 100)}%`,
          `来源：${sourceLabel(item.source)}`,
          `相关资源：${item.resourceCount}`,
        ].join('<br/>'),
      },
    }
  })
  const links = portraitNodes.value
    .filter((item) => item.parentId != null && nodeIds.has(item.parentId))
    .map((item) => ({
      source: String(item.parentId),
      target: String(item.id),
      symbol: ['none', 'arrow'],
      symbolSize: [0, 9],
    }))

  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: {
      top: 4,
      data: ['学科', '知识领域', '知识点'],
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 9],
      categories: [
        { name: '学科' },
        { name: '知识领域' },
        { name: '知识点' },
      ],
      force: {
        repulsion: 260,
        edgeLength: [80, 150],
        gravity: 0.07,
      },
      lineStyle: {
        color: '#c0c4cc',
        width: 1.2,
        curveness: 0.06,
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 2.4, color: '#409eff' },
      },
      label: {
        color: '#303133',
        fontSize: 12,
        lineHeight: 16,
      },
      data: nodes,
      links,
    }],
  }, true)
  chart.resize()
}

async function loadPortrait() {
  loading.value = true
  try {
    const [tree, ability] = await Promise.all([
      courseApi.listKnowledgePointTree({ activeOnly: true }).catch(() => []),
      learnApi.getAbilityMap().catch(() => []),
    ])
    knowledgePoints.value = tree
    abilityMap.value = ability
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

watch(portraitNodes, async () => {
  if (!hasAbilityData.value) {
    chart?.dispose()
    chart = null
    return
  }
  await nextTick()
  renderChart()
}, { deep: true })

onMounted(async () => {
  await loadPortrait()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

function handleResize() {
  chart?.resize()
}
</script>

<style scoped>
.ability-portrait-panel {
  margin-top: 20px;
}

.portrait-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.portrait-title {
  color: #303133;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}

.portrait-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.portrait-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 14px;
}

.summary-item {
  min-height: 88px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 10px;
  color: #303133;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.summary-text {
  margin-top: 12px;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
}

.legend-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.portrait-chart {
  min-height: 560px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

@media (max-width: 768px) {
  .portrait-header {
    align-items: stretch;
    flex-direction: column;
  }

  .portrait-summary {
    grid-template-columns: 1fr;
  }

  .portrait-chart {
    min-height: 480px;
  }
}
</style>
