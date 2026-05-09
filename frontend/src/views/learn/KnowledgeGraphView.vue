<template>
  <div :class="embedded ? 'knowledge-graph-panel' : 'page-container'" v-loading="loading">
    <div v-if="!embedded" class="page-header">
      <span class="page-title">知识图谱</span>
      <div class="header-actions">
        <el-select
          v-model="selectedRootId"
          filterable
          clearable
          placeholder="选择图谱范围"
          class="scope-select"
          @change="loadGraph"
          @clear="loadGraph"
        >
          <el-option
            v-for="item in knowledgePointOptions"
            :key="item.id"
            :label="item.path"
            :value="item.id"
          >
            <div class="option-row">
              <span>{{ item.path }}</span>
              <el-tag size="small" effect="plain">{{ knowledgeTypeLabel(item.nodeType) }}</el-tag>
            </div>
          </el-option>
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadGraph">刷新</el-button>
      </div>
    </div>
    <div v-else class="embedded-header">
      <div>
        <div class="embedded-title">知识图谱</div>
        <div class="embedded-subtitle">全平台课程资源关联的知识点覆盖情况</div>
      </div>
      <div class="header-actions">
        <el-select
          v-model="selectedRootId"
          filterable
          clearable
          placeholder="选择图谱范围"
          class="scope-select"
          @change="loadGraph"
          @clear="loadGraph"
        >
          <el-option
            v-for="item in knowledgePointOptions"
            :key="item.id"
            :label="item.path"
            :value="item.id"
          >
            <div class="option-row">
              <span>{{ item.path }}</span>
              <el-tag size="small" effect="plain">{{ knowledgeTypeLabel(item.nodeType) }}</el-tag>
            </div>
          </el-option>
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadGraph">刷新</el-button>
      </div>
    </div>

    <section class="summary-grid">
      <div class="summary-item">
        <div class="summary-label">当前范围</div>
        <div class="summary-value summary-text">{{ graph?.rootPath || '全平台知识点' }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">知识点数量</div>
        <div class="summary-value">{{ graph?.totalKnowledgePoints ?? 0 }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">已关联知识点</div>
        <div class="summary-value">{{ graph?.coveredKnowledgePoints ?? 0 }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">资源关联数</div>
        <div class="summary-value">{{ graph?.totalResourceRelations ?? 0 }}</div>
      </div>
    </section>

    <section class="legend-row">
      <span v-for="item in coverageLegend" :key="item.level" class="legend-item">
        <span class="legend-dot" :style="{ backgroundColor: item.color }" />
        {{ item.label }}
      </span>
    </section>

    <el-empty
      v-if="!loading && (!graph || graph.nodes.length === 0)"
      description="当前范围下还没有可展示的知识点。"
    />

    <section v-else class="graph-layout">
      <div ref="chartEl" class="graph-chart" />
      <aside class="node-panel">
        <div class="panel-title">资源覆盖排行</div>
        <div v-if="rankedNodes.length === 0" class="panel-empty">暂无资源关联数据</div>
        <div v-else class="node-list">
          <button
            v-for="item in rankedNodes"
            :key="item.id"
            type="button"
            class="node-item"
            @click="focusNode(item.id)"
          >
            <span class="node-dot" :style="{ backgroundColor: item.color }" />
            <span class="node-main">
              <span class="node-name">{{ item.name }}</span>
              <span class="node-path">{{ item.path }}</span>
            </span>
            <span class="node-count">{{ item.resourceCount }}</span>
          </button>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { courseApi } from '@/api/course'
import type { KnowledgeGraph, KnowledgeGraphNode, KnowledgePointNode } from '@/types'

type KnowledgePointOption = Pick<KnowledgePointNode, 'id' | 'nodeType' | 'path'>

withDefaults(defineProps<{ embedded?: boolean }>(), {
  embedded: false,
})

const chartEl = ref<HTMLElement>()
const loading = ref(false)
const selectedRootId = ref<number | null>(null)
const knowledgePointTree = ref<KnowledgePointNode[]>([])
const graph = ref<KnowledgeGraph | null>(null)
let chart: echarts.ECharts | null = null

const coverageLegend = [
  { level: 'NONE', label: '无关联资源', color: '#c0c4cc' },
  { level: 'LOW', label: '资源较少', color: '#f56c6c' },
  { level: 'MEDIUM', label: '资源适中', color: '#e6a23c' },
  { level: 'HIGH', label: '资源较多', color: '#409eff' },
  { level: 'VERY_HIGH', label: '资源充足', color: '#67c23a' },
]

const knowledgePointOptions = computed<KnowledgePointOption[]>(() => {
  const items: KnowledgePointOption[] = []
  const walk = (nodes: KnowledgePointNode[]) => {
    nodes.forEach((node) => {
      if (node.active !== false) {
        items.push({ id: node.id, nodeType: node.nodeType, path: node.path })
      }
      if (node.children?.length) walk(node.children)
    })
  }
  walk(knowledgePointTree.value)
  return items
})

const rankedNodes = computed(() => [...(graph.value?.nodes ?? [])]
  .sort((left, right) => right.resourceCount - left.resourceCount || left.path.localeCompare(right.path, 'zh-CN'))
  .slice(0, 12))

function knowledgeTypeLabel(type: KnowledgePointNode['nodeType']) {
  return { SUBJECT: '学科', DOMAIN: '知识领域', POINT: '知识点' }[type] ?? type
}

function nodeCategory(type: KnowledgeGraphNode['nodeType']) {
  return { SUBJECT: 0, DOMAIN: 1, POINT: 2 }[type] ?? 2
}

function nodeSymbolSize(item: KnowledgeGraphNode) {
  const base = item.nodeType === 'SUBJECT' ? 44 : item.nodeType === 'DOMAIN' ? 36 : 28
  return Math.min(72, base + Math.sqrt(Math.max(0, item.resourceCount)) * 5)
}

function renderGraph() {
  if (!chartEl.value || !graph.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const nodes = graph.value.nodes.map((item) => ({
    id: String(item.id),
    name: item.name,
    value: item.resourceCount,
    category: nodeCategory(item.nodeType),
    symbolSize: nodeSymbolSize(item),
    itemStyle: { color: item.color },
    label: {
      show: true,
      formatter: item.resourceCount > 0 ? `${item.name}\n${item.resourceCount}` : item.name,
    },
    tooltip: {
      formatter: [
        `<strong>${item.name}</strong>`,
        `路径：${item.path}`,
        `子树资源数：${item.resourceCount}`,
        `直接关联资源：${item.directResourceCount}`,
      ].join('<br/>'),
    },
  }))
  const links = graph.value.edges.map((item) => ({
    source: String(item.source),
    target: String(item.target),
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
      categories: [
        { name: '学科' },
        { name: '知识领域' },
        { name: '知识点' },
      ],
      force: {
        repulsion: 260,
        edgeLength: [70, 150],
        gravity: 0.08,
      },
      lineStyle: {
        color: '#bfc5d2',
        width: 1.4,
        curveness: 0.08,
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 2.4 },
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

async function loadKnowledgePoints() {
  knowledgePointTree.value = await courseApi.listKnowledgePointTree({ activeOnly: true })
}

async function loadGraph() {
  loading.value = true
  try {
    graph.value = await courseApi.getKnowledgeGraph({
      rootId: selectedRootId.value,
      activeOnly: true,
    })
    await nextTick()
    renderGraph()
  } finally {
    loading.value = false
  }
}

function focusNode(id: number) {
  if (!chart) return
  chart.dispatchAction({ type: 'focusNodeAdjacency', seriesIndex: 0, dataIndex: graph.value?.nodes.findIndex((item) => item.id === id) ?? -1 })
}

watch(graph, async () => {
  await nextTick()
  renderGraph()
})

onMounted(async () => {
  await loadKnowledgePoints()
  await loadGraph()
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
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.knowledge-graph-panel {
  margin-top: 20px;
}

.embedded-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.embedded-title {
  color: #303133;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}

.embedded-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.scope-select {
  width: min(460px, 48vw);
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 14px;
}

.summary-item {
  min-height: 96px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.summary-label {
  font-size: 13px;
  color: #909399;
}

.summary-value {
  margin-top: 10px;
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.summary-text {
  font-size: 18px;
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

.legend-dot,
.node-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.graph-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  min-height: 620px;
}

.graph-chart {
  min-height: 620px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.node-panel {
  min-height: 620px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 14px;
}

.panel-empty {
  color: #909399;
  font-size: 13px;
}

.node-list {
  display: grid;
  gap: 10px;
}

.node-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.node-item:hover {
  border-color: #409eff;
}

.node-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.node-name {
  color: #303133;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-path {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-count {
  color: #303133;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .graph-layout {
    grid-template-columns: 1fr;
  }

  .node-panel {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .embedded-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }

  .scope-select {
    width: 100%;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .graph-chart {
    min-height: 520px;
  }
}
</style>
