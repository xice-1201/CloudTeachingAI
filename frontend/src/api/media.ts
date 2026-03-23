import request from '@/utils/request'

export const mediaApi = {
  getPresignedUrl: (fileKey: string): Promise<{ url: string }> =>
    request.get(`/media/presigned-url`, { params: { key: fileKey } }),

  getVideoStreamUrl: (resourceId: string): Promise<{ url: string }> =>
    request.get(`/media/video/${resourceId}/stream-url`),

  deleteFile: (fileKey: string): Promise<void> =>
    request.delete(`/media/files`, { params: { key: fileKey } }),
}
