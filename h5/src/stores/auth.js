import { defineStore } from 'pinia'
import { fetchMe } from '../api/h5'

let meRequestSequence = 0

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('h5_token') || '',
    me: null
  }),
  getters: {
    loggedIn: state => !!state.token,
    isMember: state => !!state.me && state.me.role === 'MEMBER',
    isStaff: state => !!state.me && state.me.role === 'STAFF',
    isStationAdmin: state => !!state.me && state.me.role === 'STATION_ADMIN',
    phoneBound: state => !!state.me && !!state.me.phoneBound
  },
  actions: {
    setToken(token) {
      if (this.token !== token) {
        meRequestSequence++
        this.me = null
      }
      this.token = token
      localStorage.setItem('h5_token', token)
    },
    async loadMe() {
      const sequence = ++meRequestSequence
      const requestedToken = this.token
      const res = await fetchMe()
      if (sequence === meRequestSequence && requestedToken === this.token) this.me = res.data
      return this.me
    },
    logout() {
      meRequestSequence++
      this.token = ''
      this.me = null
      localStorage.removeItem('h5_token')
    }
  }
})
