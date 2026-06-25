<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  /** ECharts option 对象 */
  option: { type: Object, required: true },
  /** 图表高度 (px) */
  height: { type: Number, default: 300 },
})

const chartRef = ref(null)
let chart = null

onMounted(() => {
  chart = echarts.init(chartRef.value)
  chart.setOption(props.option)
})

onUnmounted(() => {
  chart?.dispose()
})

watch(
  () => props.option,
  (newOption) => {
    chart?.setOption(newOption, true)
  },
  { deep: true }
)

// 窗口大小变化时自适应
const handleResize = () => chart?.resize()
window.addEventListener('resize', handleResize)
onUnmounted(() => window.removeEventListener('resize', handleResize))
</script>
