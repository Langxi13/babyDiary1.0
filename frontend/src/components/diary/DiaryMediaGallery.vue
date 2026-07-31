<template>
  <section v-if="media.length" class="diary-media-gallery">
    <figure v-for="(item, index) in media" :key="item.id">
      <MediaImage
        v-if="item.mediaType === 'IMAGE'"
        :media="item"
        :preview-media="imageMedia"
        :initial-index="imageIndex(index)"
        fit="cover"
      />
      <audio
        v-else-if="item.mediaType === 'AUDIO'"
        controls
        preload="metadata"
        :src="mediaOriginalUrl(item)"
      />
      <video
        v-else
        controls
        preload="metadata"
        playsinline
        :poster="mediaPosterUrl(item)"
        :src="mediaPlaybackUrl(item)"
      />
      <figcaption v-if="item.caption">{{ item.caption }}</figcaption>
    </figure>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import MediaImage from '@/components/diary/MediaImage.vue'
import {
  mediaOriginalUrl,
  mediaPlaybackUrl,
  mediaPosterUrl
} from '@/api/models'

const props = defineProps({
  media: { type: Array, default: () => [] }
})

const imageMedia = computed(() => props.media.filter(item => item.mediaType === 'IMAGE'))

const imageIndex = mediaIndex => props.media
  .slice(0, mediaIndex)
  .filter(item => item.mediaType === 'IMAGE')
  .length
</script>

<style scoped>
.diary-media-gallery {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

figure {
  min-width: 0;
  margin: 0;
  border: 1px solid #e7ddd8;
  border-radius: 8px;
  overflow: hidden;
  background: #faf8f6;
}

:deep(.el-image), video { width: 100%; aspect-ratio: 16 / 10; display: block; object-fit: cover; }
audio { width: calc(100% - 24px); margin: 18px 12px; }
figcaption { padding: 10px 12px; color: #6e645f; font-size: 13px; line-height: 1.5; }

@media (max-width: 600px) {
  .diary-media-gallery { grid-template-columns: 1fr; gap: 9px; }
  :deep(.el-image), video { aspect-ratio: 4 / 3; }
}
</style>
