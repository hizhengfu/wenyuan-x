import {defineStore} from 'pinia';
import {userInfo, login} from '@/modules/sys/api/auth'
import {UserState} from './types';

const useUserStore = defineStore('user', {
  state: (): UserState => ({}),

  getters: {
    userInfo(state: UserState): UserState {
      return {...state};
    },
    roles(state: UserState): string[] {
      return state.roles || [];
    },
  },

  actions: {
    //登陆
    login(data: any) {
      login(data).then((resp) => {
        console.log(resp)
      })
    },
    //登出
    logout() {

    },
    fetchInfo() {
      return userInfo().then((resp) => {
        console.log(resp)
      })
    }
  },
});

export default useUserStore;
