export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export const isUploadSizeAllowed = (file, maxBytes = MAX_UPLOAD_BYTES) =>
  Number.isFinite(file?.size) && file.size >= 0 && file.size <= maxBytes

export const uploadSizeMessage = (maxBytes = MAX_UPLOAD_BYTES) =>
  `上传文件不能超过${Math.round(maxBytes / 1024 / 1024)}MiB`
