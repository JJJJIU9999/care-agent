import { reactive } from 'vue'

const TOKEN_KEY = 'careagent.token'
const ROLE_KEY = 'careagent.role'
const NAME_KEY = 'careagent.username'

type Role = 'USER' | 'ADMIN' | null

interface AuthState {
  token: string
  role: Role
  username: string
}

const state = reactive<AuthState>({
  token: localStorage.getItem(TOKEN_KEY) ?? '',
  role: (localStorage.getItem(ROLE_KEY) as Role) ?? null,
  username: localStorage.getItem(NAME_KEY) ?? '',
})

export const auth = {
  state,
  get token() {
    return state.token
  },
  get isAuthed() {
    return state.token.length > 0
  },
  get isAdmin() {
    return state.role === 'ADMIN'
  },
  get username() {
    return state.username
  },
  setSession(token: string, role: string, username: string) {
    state.token = token
    state.role = role as Role
    state.username = username
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(ROLE_KEY, role)
    localStorage.setItem(NAME_KEY, username)
  },
  clear() {
    state.token = ''
    state.role = null
    state.username = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(ROLE_KEY)
    localStorage.removeItem(NAME_KEY)
  },
}
