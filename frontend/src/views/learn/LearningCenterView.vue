<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">学习中心</span>
    </div>

    <el-row :gutter="20" class="summary-row">
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never">
          <div class="summary-label">已画像知识点</div>
          <div class="summary-value">{{ abilityMap.length }}</div>
          <div class="summary-desc">当前已形成能力画像的知识点数量</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never">
          <div class="summary-label">平均掌握度</div>
          <div class="summary-value">{{ averageMasteryText }}</div>
          <div class="summary-desc">综合测试和学习进度得出的阶段性水平</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :lg="8">
        <el-card shadow="never">
          <div class="summary-label">优先补强</div>
          <div class="summary-value summary-inline">{{ weakestNamesText }}</div>
          <div class="summary-desc">建议优先回到这些知识点补学相关资源</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" header="能力雷达图" class="chart-card">
          <div v-if="abilityMap.length === 0" class="empty-chart">
            <el-empty description="还没有能力画像数据。完成能力测试，或先学习已标注的课程资源后再回来查看。" />
          </div>
          <div v-else ref="chartEl" class="chart-view" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" header="快捷入口" class="side-card">
          <div class="quick-actions">
            <button type="button" class="action-item" @click="$router.push('/learning/ability-test')">
              <div class="action-title">开始能力测试</div>
              <div class="action-desc">按知识点范围进行一轮掌握度诊断</div>
            </button>
            <button type="button" class="action-item" @click="$router.push('/learning/path')">
              <div class="action-title">查看学习路线</div>
              <div class="action-desc">基于能力图谱、学习进度和课程资源生成下一步建议</div>
            </button>
          </div>
        </el-card>

        <el-card shadow="never" header="知识点掌握情况" class="side-card">
          <div v-if="abilityMap.length === 0">
            <el-empty description="暂无能力画像" :image-size="64" />
          </div>
          <div v-else class="ability-list">
            <div v-for="item in sortedAbilityMap" :key="item.knowledgePointId" class="ability-item">
              <div class="ability-meta">
                <div class="ability-name">{{ item.knowledgePointName }}</div>
                <div class="ability-path">{{ item.knowledgePointPath || item.knowledgePointName }}</div>
              </div>
              <el-progress
                :percentage="Math.round(item.masteryLevel * 100)"
                :color="masteryColor(item.masteryLevel)"
                :stroke-width="8"
              />
              <div class="ability-foot">
                <span>来源：{{ sourceLabel(item.source) }}</span>
                <span>资源数：{{ item.resourceCount }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { learnApi } from '@/api/learn'
import type { AbilityMap } from '@/types'

const chartEl = ref<HTMLElement>()
const abilityMap = ref<AbilityMap[]>([])
let chart: echarts.ECharts | null = null

const sortedAbilityMap = computed(() => [...abilityMap.value].sort((left, right) => left.masteryLevel - right.masteryLevel))
const chartAbilityMap = computed(() => [...sortedAbilityMap.value].slice(0, 8))
const averageMastery = computed(() => {
  if (abilityMap.value.length === 0) return 0
  const total = abilityMap.value.reduce((sum, item) => sum + item.masteryLevel, 0)
  return total / abilityMap.value.length
})
const averageMasteryText = computed(() => `${Math.round(averageMastery.value * 100)}%`)
const weakestNamesText = computed(() => {
  if (abilityMap.value.length === 0) return '暂无'
  return [...abilityMap.value]
    .sort((left, right) => left.masteryLevel - right.masteryLevel)
    .slice(0, 3)
    .map((item) => item.knowledgePointName)
    .join('、')
})

function masteryColor(level: number) {
  if (level >= 0.8) return '#67c23a'
  if (level >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

function sourceLabel(source: AbilityMap['source']) {
  return {
    TEST: '能力测试',
    TEST_AND_PROGRESS: '测试 + 学习进度',
    LEARNING_PROGRESS: '学习进度',
    COURSE_TAGS: '课程资源标签',
  }[source] ?? source
}

function renderChart(data: AbilityMap[]) {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '62%',
      splitNumber: 4,
      axisName: { color: '#606266', fontSize: 12 },
      splitArea: {
        areaStyle: {
          color: ['rgba(64, 158, 255, 0.05)', 'rgba(64, 158, 255, 0.08)'],
        },
      },
      indicator: data.map((item) => ({
        name: item.knowledgePointName,
        max: 1,
      })),
    },
    series: [{
      type: 'radar',
      data: [{
        value: data.map((item) => item.masteryLevel),
        name: '能力画像',
        areaStyle: { color: 'rgba(64, 158, 255, 0.22)' },
        lineStyle: { color: '#409eff', width: 2 },
        itemStyle: { color: '#409eff' },
      }],
    }],
  })
  chart.resize()
}

async function loadAbilityMap() {
  abilityMap.value = await learnApi.getAbilityMap().catch(() => [])
}

watch(chartAbilityMap, async (data) => {
  if (data.length === 0) {
    chart?.dispose()
    chart = null
    return
  }
  await nextTick()
  renderChart(data)
}, { deep: true })

onMounted(async () => {
  await loadAbilityMap()
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
.summary-row {
  margin-bottom: 20px;
}

.summary-label {
  font-size: 13px;
  color: #909399;
}

.summary-value {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.summary-inline {
  font-size: 22px;
}

.summary-desc {
  margin-top: 10px;
  color: #606266;
  line-height: 1.6;
  font-size: 13px;
}

.chart-card,
.side-card {
  height: 100%;
}

.chart-view {
  height: 380px;
}

.empty-chart {
  min-height: 380px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-actions {
  display: grid;
  gap: 12px;
}

.action-item {
  padding: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.action-item:hover {
  border-color: #409eff;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.12);
  transform: translateY(-1px);
}

.action-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.action-desc {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.ability-list {
  display: grid;
  gap: 16px;
}

.ability-item {
  display: grid;
  gap: 10px;
}

.ability-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ability-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.ability-path {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.ability-foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 768px) {
  .summary-value {
    font-size: 28px;
  }

  .summary-inline {
    font-size: 18px;
  }
}
</style>
