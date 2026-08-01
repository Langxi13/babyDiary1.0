import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { copyText } from '@/utils/copyText'
import { mediaThumbnailUrl } from '@/api/models'
import { releaseNativeImage } from '@/platform/nativeImages'

const ACCEPTED_IMAGE_TYPES = new Set([
  'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/heif'
])
const MAX_IMAGE_SIZE = 25 * 1024 * 1024
const MAX_DIARY_IMAGES = 50

export function useDiaryImages({ route }) {
  const fileList = ref([])
  const previewVisible = ref(false)
  const previewUrl = ref('')
  const isAndroidDevice = ref(false)
  const androidUploadHelpVisible = ref(false)
  const androidUploadUrlInput = ref(null)

  const browserUploadUrl = computed(() => {
    if (typeof window === 'undefined') {
      return route.fullPath || '/diaries/create'
    }
    return window.location.href
  })

  const selectAndroidUploadUrl = () => {
    const input = androidUploadUrlInput.value
    if (!input) return null
    input.focus()
    input.select()
    input.setSelectionRange?.(0, input.value.length)
    return input
  }

  const copyBrowserUploadUrl = async () => {
    try {
      const copied = await copyText(browserUploadUrl.value, selectAndroidUploadUrl())
      if (!copied) throw new Error('copy command returned false')
      ElMessage.success('链接已复制')
    } catch {
      ElMessage.warning('复制失败，请长按链接手动复制')
    }
  }

  const isValidImageFile = (file) => {
    const contentType = String(file?.type || '').toLowerCase()
    if (!ACCEPTED_IMAGE_TYPES.has(contentType)) {
      ElMessage.error('仅支持 JPEG、PNG、GIF、WebP、HEIC 和 HEIF 图片')
      return false
    }
    if (!file.size || file.size > MAX_IMAGE_SIZE) {
      ElMessage.error('图片大小不能超过25MB')
      return false
    }
    return true
  }

  const beforeUpload = (file) => {
    isValidImageFile(file)
    return false
  }

  const handlePreview = (file) => {
    previewUrl.value = file.url
    previewVisible.value = true
  }

  const revokeObjectUrl = (file) => {
    if (file?.url?.startsWith('blob:')) {
      URL.revokeObjectURL(file.url)
    }
    releaseNativeImage(file?.raw).catch(() => {})
  }

  const handleImageChange = (_uploadFile, uploadFiles) => {
    fileList.value = uploadFiles
      .filter(file => file.isExisting || (file.raw && isValidImageFile(file.raw)))
      .map(file => file.url || !file.raw
        ? file
        : {
            ...file,
            uploadId: file.uploadId || file.raw?.uploadId || crypto.randomUUID(),
            url: file.raw?.kind === 'native-uri'
              ? file.raw.previewUrl
              : URL.createObjectURL(file.raw)
          })
  }

  const removeImageAt = (index) => {
    const nextFiles = [...fileList.value]
    const [removedFile] = nextFiles.splice(index, 1)
    revokeObjectUrl(removedFile)
    fileList.value = nextFiles
  }

  const moveImage = (index, direction) => {
    const targetIndex = index + direction
    if (targetIndex < 0 || targetIndex >= fileList.value.length) return
    const nextFiles = [...fileList.value]
    const [movedFile] = nextFiles.splice(index, 1)
    nextFiles.splice(targetIndex, 0, movedFile)
    fileList.value = nextFiles
  }

  const handleNativeImageChange = (event) => {
    const input = event.target
    appendNativeFiles(Array.from(input.files || []))
    input.value = ''
  }

  const appendNativeFiles = (files) => {
    const available = Math.max(0, MAX_DIARY_IMAGES - fileList.value.length)
    const validFiles = Array.from(files || [])
      .filter(isValidImageFile)
    if (validFiles.length > available) {
      ElMessage.warning(`单篇日记最多添加 ${MAX_DIARY_IMAGES} 张图片`)
    }
    const acceptedFiles = validFiles
      .slice(0, available)
      .map((file, index) => ({
        name: file.name,
        uid: `native-${Date.now()}-${index}`,
        uploadId: file.uploadId || crypto.randomUUID(),
        url: file.kind === 'native-uri' ? file.previewUrl : URL.createObjectURL(file),
        raw: file
      }))

    fileList.value = [...fileList.value, ...acceptedFiles]
  }

  const buildImageSubmission = () => {
    const newImages = []
    const retainedMediaIds = []
    const mediaOrder = []
    for (const file of fileList.value) {
      if (file.raw) {
        mediaOrder.push(`new:${newImages.length}`)
        newImages.push(file.raw.kind === 'native-uri'
          ? file.raw
          : { file: file.raw, uploadId: file.uploadId || crypto.randomUUID() })
      } else if (file.isExisting && file.name) {
        retainedMediaIds.push(file.name)
        mediaOrder.push(`existing:${file.name}`)
      }
    }
    return { newImages, retainedMediaIds, mediaOrder }
  }

  const setExistingImages = (media = []) => {
    const images = media.filter(item => item?.mediaType === 'IMAGE')
    fileList.value = images.map((item, index) => ({
      name: item.id,
      url: mediaThumbnailUrl(item),
      uid: `existing-${index}`,
      isExisting: true,
      media: item
    }))
  }

  const initializeImageUpload = () => {
    isAndroidDevice.value = /Android/i.test(navigator.userAgent || '')
  }

  const disposeImages = () => {
    fileList.value.forEach(revokeObjectUrl)
  }

  return {
    fileList,
    previewVisible,
    previewUrl,
    isAndroidDevice,
    androidUploadHelpVisible,
    androidUploadUrlInput,
    browserUploadUrl,
    selectAndroidUploadUrl,
    copyBrowserUploadUrl,
    beforeUpload,
    handlePreview,
    handleImageChange,
    removeImageAt,
    moveImage,
    handleNativeImageChange,
    appendNativeFiles,
    buildImageSubmission,
    setExistingImages,
    initializeImageUpload,
    disposeImages
  }
}
