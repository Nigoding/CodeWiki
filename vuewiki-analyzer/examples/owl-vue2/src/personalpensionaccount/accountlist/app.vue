<template>
  <div>
    <button @click="search">查询</button>
  </div>
</template>

<script>
import { queryAccount } from '../api'

export default {
  mounted() {
    this.search()
  },
  methods: {
    search() {
      queryAccount({})
    }
  }
}
</script>
