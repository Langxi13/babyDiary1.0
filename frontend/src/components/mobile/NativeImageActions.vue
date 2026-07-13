<template>
  <div class="native-image-actions" :class="{ compact }">
    <el-button :disabled="disabled || busy || limit <= 0" :loading="busy === 'gallery'" @click="chooseImages">
      <el-icon><Picture /></el-icon>
      从相册选择
    </el-button>
    <el-button :disabled="disabled || !!busy || limit <= 0" :loading="busy === 'camera'" @click="takePhoto">
      <el-icon><CameraIcon /></el-icon>
      拍照
    </el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { Camera as CameraIcon, Picture } from '@element-plus/icons-vue'
import {
  chooseNativeImages,
  isNativeImageCancellation,
  takeNativePhoto
} from '@/platform/nativeImages'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'

const props = defineProps({
  limit: { type: Number, default: 20 },
  disabled: Boolean,
  compact: Boolean
})
const emit = defineEmits(['selected'])
const busy = ref('')

const run = async (source, action) => {
  if (busy.value || props.disabled || props.limit <= 0) return
  busy.value = source
  try {
    const files = await action()
    if (files.length) emit('selected', files)
  } catch (error) {
    if (!isNativeImageCancellation(error)) {
      ElMessage.error(error.message || '无法读取图片')
    }
  } finally {
    busy.value = ''
  }
}

const chooseImages = () => run('gallery', () => chooseNativeImages(props.limit))
const takePhoto = () => run('camera', takeNativePhoto)
</script>

<style scoped lang="scss">
.native-image-actions {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  :deep(.el-button) {
    width: 100%;
    min-width: 0;
    min-height: 46px;
    margin: 0;
    border-radius: 8px;
    color: #2f756d;
    border-color: #b8d2cd;
    background: #f5fbf9;
    font-weight: 700;
  }

  :deep(.el-button:active) {
    background: #e8f6f3;
  }

  &.compact :deep(.el-button) {
    min-height: 38px;
    padding-inline: 10px;
    font-size: 13px;
  }
}

@media (max-width: 360px) {
  .native-image-actions { grid-template-columns: 1fr; }
}
</style>
