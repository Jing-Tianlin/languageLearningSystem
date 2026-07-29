import client from './client'

export const userApi = {
  getUsers(params) {
    return client.get('/user/users', { params })
  },
  getUserById(id) {
    return client.get(`/user/users/${id}`)
  },
  createUser(data) {
    return client.post('/user/users', data)
  },
  updateUser(data) {
    return client.put('/user/users', data)
  },
  deleteUser(id) {
    return client.delete(`/user/users/${id}`)
  },
}
