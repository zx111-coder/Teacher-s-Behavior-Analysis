<template>
    <div ref="chart" :style="{ width: width, height: height }"></div>
  </template>
  
  <script>
  import { ref, onMounted, watch } from 'vue'
  import * as echarts from 'echarts'
  
  export default {
    name: 'DataChart',
    props: {
      width: {
        type: String,
        default: '100%'
      },
      height: {
        type: String,
        default: '400px'
      },
      options: {
        type: Object,
        required: true
      }
    },
    setup(props) {
      const chart = ref(null)
      let chartInstance = null
  
      const initChart = () => {
        if (chartInstance) {
          chartInstance.dispose()
        }
        chartInstance = echarts.init(chart.value)
        chartInstance.setOption(props.options)
      }
  
      const resizeChart = () => {
        if (chartInstance) {
          chartInstance.resize()
        }
      }
  
      onMounted(() => {
        initChart()
        window.addEventListener('resize', resizeChart)
      })
  
      watch(() => props.options, (newVal) => {
        if (chartInstance) {
          chartInstance.setOption(newVal)
        }
      }, { deep: true })
  
      return {
        chart
      }
    }
  }
  </script>