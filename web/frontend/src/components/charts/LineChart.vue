<template>
  <div class="chart-panel">
    <div class="chart-title" v-if="title">{{ title }}</div>
    <el-skeleton :loading="loading" animated :count="1">
      <template #template>
        <div :style="{ height: height + 'px', background: '#f5f7fa', borderRadius: '8px' }"></div>
      </template>
      <template #default>
        <BaseChart :option="chartOption" :height="height" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import BaseChart from './BaseChart.vue'

const props = defineProps({
  title: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  height: { type: Number, default: 300 },
  /** 折线图系列数据: [{ name, data: [] }] */
  series: { type: Array, required: true },
  /** X 轴标签 */
  xLabels: { type: Array, required: true },
})

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: {
    data: props.series.map((s) => s.name),
    bottom: 0,
  },
  grid: { left: '3%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
  xAxis: {
    type: 'category',
    data: props.xLabels,
    boundaryGap: false,
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: props.series.map((s) => ({
    name: s.name,
    type: 'line',
    data: s.data,
    smooth: true,
    symbol: 'none',
    lineStyle: { width: 2 },
    areaStyle: { opacity: 0.08 },
  })),
}))
</script>

<style scoped>
.chart-panel {
  margin-bottom: 16px;
}
.chart-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 12px;
}
</style>
