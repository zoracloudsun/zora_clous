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
})

const COLORS = ['#1677ff', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4']

const chartOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', right: '5%', top: 'center' },
  series: [{
    type: 'pie',
    radius: ['45%', '72%'],
    center: ['40%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: {
      borderRadius: 4,
      borderColor: '#fff',
      borderWidth: 2,
    },
    label: { show: false },
    emphasis: {
      label: { show: true, fontSize: 14, fontWeight: 'bold' },
    },
    data: props.data.map((d, i) => ({
      ...d,
      itemStyle: { color: COLORS[i % COLORS.length] },
    })),
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
