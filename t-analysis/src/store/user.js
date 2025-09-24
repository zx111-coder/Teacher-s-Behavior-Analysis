import { defineStore } from 'pinia';
//名为userInfo的状态存储,state是存储状态
export const useUserStore = defineStore('userInfo', {
  state: () => ({
    userid: 0, // 用户ID
    username: 'user', // 默认值为用户
    passworld:'',
    role:'user',
    token:0
  }),
  //actions修改状态的方法
  actions: {
    setUserInfo(Form) { 
      this.userid = Form.id
      this.username = Form.username
      this.passworld = Form.passworld
      this.role = Form.role
      // 将用户信息存储到 sessionStorage 中
      localStorage.setItem('userinfo', JSON.stringify(Form))
    },
     // 新增：清除用户名的方法
     clearUserInfo() {
      this.userid = 0 // 清除用户ID
      this.username = 'user' // 默认值为用户
      this.passworld = ''
      this.role = 'user'// 删除 localStorage 中的键
      this.token = 0 // 清除token
      // 清除状态
      localStorage.removeItem('userinfo')
    }
  },
  persist: true
});