<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">学习中心</span>
    </div>

    <el-row :gutter="20">
      <el-col :span="14">
        <el-card shadow="never" header="能力图谱">
          <div ref="chartEl" style="height: 360px" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" header="快捷入口" style="margin-bottom: 20px">
          <div class="quick-actions">
            <div class="action-item" @click="$router.push('/learning/ability-test')">
              <el-icon :size="28" color="#409eff"><TrendCharts /></el-icon>
              <span>能力测试</span>
            </div>
            <div class="action-item" @click="$router.push('/learning/path')">
              <el-icon :size="28" color="#67c23a"><Guide /></el-icon>
              <span>学习路线</span>
            </div>
          </div>
        </el-card>
        <el-card shadow="never" header="知识点掌握情况">
          <div v-for="item in abilityMap" :key="item.knowledgePointId" class="ability-item">
            <span class="kp-name">{{ item.knowledgePointName }}</span>
            <el-progress
              :percentage="Math.round(item.masteryLevel * 100)"
              :color="masteryColor(item.masteryLevel)"
              :stroke-width="8"
              style="flex: 1"
            />
          </div>
          <el-empty v-if="abilityMap.length === 0" description="暂无测试数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { TrendCharts, Guide } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { learnApi } from '@/api/learn'
import type { AbilityMap } from '@/types'

const chartEl = ref<HTMLElement>()
const abilityMap = ref<AbilityMap[]>([])

function masteryColor(level: number) {
  if (level >= 0.8) return '#67c23a'
  if (level >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

function renderChart(data: AbilityMap[]) {
  if (!chartEl.value || data.length === 0) return
  const chart = echarts.init(chartEl.value)
  chart.setOption({
    radar: {
      indicator: data.map((d) => ({ name: d.knowledgePointName, max: 1 })),
    },
    series: [{
      type: 'radar',
      data: [{
        value: data.map((d) => d.masteryLevel),
        name: '掌握程度',
        areaStyle: { opacity: 0.3 },
      }],
    }],
  })
}

onMounted(async () => {
  abilityMap.value = await learnApi.getAbilityMap().catch(() => [])
  renderChart(abilityMap.value)
})
</script>

<style scoped>
.quick-actions {
  display: flex;
  gap: 20px;
}
.action-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: border-color 0.2s;
}
.action-item:hover { border-color: #409eff; color: #409eff; }
.ability-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.kp-name {
  width: 100px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
