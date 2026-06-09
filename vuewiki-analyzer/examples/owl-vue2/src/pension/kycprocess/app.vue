<template>
  <div>
    <step-one @submit="handleSubmit" />
    <button @click="refresh">刷新</button>
  </div>
</template>

<script>
import StepOne from './components/StepOne.vue'
import { queryKycStatus } from '../api'

export default {
  components: { StepOne },
  created() {
    this.refresh()
  },
  methods: {
    refresh() {
      queryKycStatus({ from: 'app' })
    },
    handleSubmit() {
      this.refresh()
    }
  }
}
</script>
