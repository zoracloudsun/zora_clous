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
  /** 数据: [{ name, value }] */
  data: { type: Array, required: true },
  /** X 轴标签 */
  xLabels: { type: Array, default: () => [] },
  /** X 轴数据 key */
  xKey: { type: String, default: 'name' },
  /** Y 轴数据 key */
  yKey: { type: String, default: 'value' },
})

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: '3%', right: '4%', bottom: '8%', top: '8%', containLabel: true },
  xAxis: {
    type: 'category',
    data: props.xLabels.length > 0
      ? props.xLabels
      : props.data.map((d) => d[props.xKey]),
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{
    type: 'bar',
    data: props.data.map((d) => d[props.yKey]),
    itemStyle: {
      borderRadius: [4, 4, 0, 0],
      color: '#1677ff',
    },
    barMaxWidth: 24,
  }],
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
