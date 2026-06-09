import request from '@/common/request'

export function queryKycStatus(params) {
  return request({
    url: '/pension/kyc/status',
    method: 'post',
    data: params
  })
}

export function submitKycInfo(params) {
  return request({
    url: '/pension/kyc/submit',
    method: 'post',
    data: params
  })
}
