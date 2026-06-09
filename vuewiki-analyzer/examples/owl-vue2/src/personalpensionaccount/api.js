import request from '@/common/request'

export const queryAccount = (params) => request({
  url: '/personal-pension/account/list',
  method: 'get',
  params
})
