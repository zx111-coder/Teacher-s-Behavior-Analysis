
import { defineStore } from 'pinia';
import { getUserRole } from '../utils/auth';

export const useRootStore = defineStore('root', {
  state: () => ({
    user: null,
    videoData: null,
    analysisResults: null
  }),
  actions: {
    async fetchUser() {
      const role = getUserRole();
      this.user = { role };
    },
    setVideoData(data) {
      this.videoData = data;
    },
    setAnalysisResults(results) {
      this.analysisResults = results;
    }
  },
  getters: {
    isAdmin: (state) => state.user?.role === 'admin'
  }
});