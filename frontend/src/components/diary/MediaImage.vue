<template>
  <el-image
    v-bind="$attrs"
    :src="mediaThumbnailUrl(media)"
    :preview-src-list="previewUrls"
    :initial-index="initialIndex"
    :preview-teleported="previewTeleported"
    :fit="fit"
    :lazy="lazy"
  >
    <template v-if="$slots.placeholder" #placeholder><slot name="placeholder" /></template>
    <template v-if="$slots.error" #error><slot name="error" /></template>
    <template #toolbar="{ actions, reset, activeIndex }">
      <el-icon title="缩小" @click="actions('zoomOut')"><ZoomOut /></el-icon>
      <el-icon title="放大" @click="actions('zoomIn')"><ZoomIn /></el-icon>
      <i class="el-image-viewer__actions__divider" />
      <el-icon title="适应窗口" @click="reset"><FullScreen /></el-icon>
      <i class="el-image-viewer__actions__divider" />
      <el-icon title="向左旋转" @click="actions('anticlockwise')"><RefreshLeft /></el-icon>
      <el-icon title="向右旋转" @click="actions('clockwise')"><RefreshRight /></el-icon>
      <template v-if="canLoadOriginal(activeIndex) || isLoadingOriginal(activeIndex)">
        <i class="el-image-viewer__actions__divider" />
        <button
          type="button"
          class="media-viewer-original-action"
          :disabled="isLoadingOriginal(activeIndex)"
          title="按需加载未经缩放的原始文件"
          @click.stop="showOriginal(activeIndex)"
        >
          <el-icon><Picture /></el-icon>
          <span class="media-viewer-original-full">
            {{ isLoadingOriginal(activeIndex) ? '原图加载中' : '查看原图' }}
          </span>
          <span class="media-viewer-original-short">
            {{ isLoadingOriginal(activeIndex) ? '加载中' : '原图' }}
          </span>
        </button>
      </template>
    </template>
  </el-image>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { FullScreen, Picture, RefreshLeft, RefreshRight, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { mediaOriginalUrl, mediaPreviewUrl, mediaThumbnailUrl } from '@/api/models'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/image/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  media: { type: Object, required: true },
  previewMedia: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  fit: { type: String, default: 'cover' },
  lazy: { type: Boolean, default: true },
  previewTeleported: { type: Boolean, default: true }
})

const originalKeys = ref(new Set())
const loadingOriginalKeys = ref(new Set())
const items = computed(() => props.previewMedia.length ? props.previewMedia : [props.media])
const itemKey = (item, index) => item?.id || mediaOriginalUrl(item) || String(index)
const previewUrls = computed(() => items.value.map((item, index) => (
  originalKeys.value.has(itemKey(item, index)) ? mediaOriginalUrl(item) : mediaPreviewUrl(item)
)))

const canLoadOriginal = index => {
  const item = items.value[index]
  return !!item && !originalKeys.value.has(itemKey(item, index)) &&
    !!mediaOriginalUrl(item) && mediaOriginalUrl(item) !== mediaPreviewUrl(item)
}

const isLoadingOriginal = index => loadingOriginalKeys.value.has(itemKey(items.value[index], index))
const setLoadingOriginal = (index, loading) => {
  const key = itemKey(items.value[index], index)
  const next = new Set(loadingOriginalKeys.value)
  if (loading) next.add(key)
  else next.delete(key)
  loadingOriginalKeys.value = next
}

const showOriginal = async index => {
  if (!canLoadOriginal(index)) return
  const url = mediaOriginalUrl(items.value[index])
  setLoadingOriginal(index, true)
  try {
    await new Promise((resolve, reject) => {
      const image = new Image()
      image.onload = resolve
      image.onerror = reject
      image.src = url
    })
    originalKeys.value = new Set(originalKeys.value).add(itemKey(items.value[index], index))
  } catch {
    ElMessage.error('原图加载失败，请检查网络后重试')
  } finally {
    setLoadingOriginal(index, false)
  }
}
</script>

<style>
.media-viewer-original-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  border: 0;
  padding: 0 4px;
  color: inherit;
  background: transparent;
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.media-viewer-original-action:disabled {
  cursor: wait;
  opacity: 0.72;
}

.media-viewer-original-action .el-icon {
  margin: 0;
}

.media-viewer-original-short {
  display: none;
}

@media (max-width: 600px) {
  .el-image-viewer__actions {
    width: min(94vw, 390px);
  }

  .el-image-viewer__actions__inner {
    gap: 6px;
  }

  .media-viewer-original-action {
    font-size: 13px;
  }

  .media-viewer-original-full {
    display: none;
  }

  .media-viewer-original-short {
    display: inline;
  }
}
</style>
