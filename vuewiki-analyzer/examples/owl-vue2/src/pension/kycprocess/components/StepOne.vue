<template>
  <form @submit="submit">
    <button type="submit">提交</button>
  </form>
</template>

<script>
import { submitKycInfo } from '../../api'

export default {
  methods: {
    submit() {
      submitKycInfo({ step: 1 })
    }
  }
}
</script>
