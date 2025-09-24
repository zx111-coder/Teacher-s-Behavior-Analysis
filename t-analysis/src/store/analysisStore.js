import { defineStore } from 'pinia'

export const useAnalysisStore = defineStore('analysis', {
  state: () => ({
    result: null
  }),
  actions: {
    setAnalysisResult(payload) {
      this.result = payload
    },
    clearAnalysisResult() {
      this.result = null
    }
  },
  getters: {
    getAnalysisResult: (state) => state.result
  }
})