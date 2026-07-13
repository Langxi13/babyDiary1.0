import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { originalImageUrl } from '@/utils/imageUrl'
import { copyText } from '@/utils/copyText'

const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const MAX_DIARY_IMAGES = 50

export function useDiaryImages({ route, isEdit }) {
  const fileList = ref([])
  const previewVisible = ref(false)
  const previewUrl = ref('')
  const initialImageCount = ref(0)
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
      ElMessage.error('仅支持 JPEG、PNG、GIF 和 WebP 图片')
      return false
    }
    if (!file.size || file.size > MAX_IMAGE_SIZE) {
      ElMessage.error('图片大小不能超过10MB')
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
  }

  const handleImageChange = (_uploadFile, uploadFiles) => {
    fileList.value = uploadFiles
      .filter(file => file.isExisting || (file.raw && isValidImageFile(file.raw)))
      .map(file => file.url || !file.raw
        ? file
        : { ...file, url: URL.createObjectURL(file.raw) })
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
        url: URL.createObjectURL(file),
        raw: file
      }))

    fileList.value = [...fileList.value, ...acceptedFiles]
  }

  const imageOrderForFile = (file, newImageIndex) => {
    if (file.raw) return `new:${newImageIndex}`
    if (file.isExisting && file.name) return `existing:${file.name}`
    return ''
  }

  const appendImagesToFormData = (formData) => {
    if (!isEdit.value) {
      fileList.value.filter(file => file.raw).forEach(file => {
        formData.append('imageFiles', file.raw)
      })
      return
    }

    let newImageIndex = 0
    for (const file of fileList.value) {
      const orderEntry = imageOrderForFile(file, newImageIndex)
      if (file.raw) {
        formData.append('imageFiles', file.raw)
        newImageIndex += 1
      } else if (file.isExisting && file.name) {
        formData.append('retainedImagePaths', file.name)
      }
      if (orderEntry) {
        formData.append('imageOrder', orderEntry)
      }
    }

    const retainedCount = fileList.value.filter(file => file.isExisting && file.name).length
    if (initialImageCount.value > 0 && retainedCount === 0) {
      formData.append('clearImages', 'true')
    }
  }

  const setExistingImages = (imagePaths = []) => {
    initialImageCount.value = imagePaths.length
    fileList.value = imagePaths.map((imagePath, index) => ({
      name: imagePath,
      url: originalImageUrl(imagePath),
      uid: `existing-${index}`,
      isExisting: true
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
    appendImagesToFormData,
    setExistingImages,
    initializeImageUpload,
    disposeImages
  }
}
