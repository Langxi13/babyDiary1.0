<template>
  <div class="page-shell">
    <main class="page-container album-detail-page">
      <div class="page-title-row">
        <div>
          <el-button text @click="router.push('/album')">
            <el-icon><ArrowLeft /></el-icon>
            返回相册
          </el-button>
          <h1>{{ albumTitle }}</h1>
          <p>{{ albumDescription }}</p>
        </div>
        <div class="detail-stats">
          <span>{{ totalPhotos }} 张照片</span>
          <strong>{{ editableAlbum ? '可编辑相册' : '系统相册' }}</strong>
        </div>
      </div>

      <div v-loading="loadingPhotos" class="album-photo-grid">
        <el-empty v-if="!loadingPhotos && photos.length === 0" description="暂无照片" />
        <article v-for="(photo, index) in photos" :key="photo.imageId" class="photo-card">
          <el-image
            :src="thumbnailImageUrl(photo.imagePath)"
            :preview-src-list="previewImages"
            :initial-index="index"
            fit="cover"
            class="photo"
            :preview-teleported="true"
            lazy
          >
            <template #placeholder>
              <div class="image-state image-loading" />
            </template>
            <template #error>
              <div class="image-state">
                <el-icon><Picture /></el-icon>
                <span>图片暂不可用</span>
              </div>
            </template>
          </el-image>
          <button
            class="favorite-btn"
            :class="{ active: photo.favorite }"
            :disabled="!!favoriteBusyId"
            @click.stop="toggleFavorite(photo)"
          >
            <el-icon><StarFilled /></el-icon>
          </button>
          <button v-if="editableAlbum" class="remove-photo-btn" @click.stop="removePhoto(photo.imageId)">
            移除
          </button>
          <div class="photo-meta" @click.stop="router.push(`/diaries/${photo.diaryId}`)">
            <strong>{{ photo.diaryTitle }}</strong>
            <span>{{ formatChineseDate(photo.diaryDate) }}</span>
          </div>
        </article>
      </div>

      <div v-if="photos.length" class="photo-page-footer">
        <span>已显示 {{ photos.length }} / {{ totalPhotos }} 张</span>
        <el-button v-if="hasMorePhotos" :loading="loadingMore" @click="loadMorePhotos">
          加载更多
        </el-button>
        <strong v-else>已显示全部</strong>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ArrowLeft, Picture, StarFilled } from '@element-plus/icons-vue'
import { albumApi } from '@/api/album'
import { photoApi } from '@/api/experience'
import { formatChineseDate } from '@/utils/dateDisplay'
import { originalImageUrl, thumbnailImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/image/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'

const route = useRoute()
const router = useRouter()
const PHOTO_PAGE_SIZE = 24
const loadingPhotos = ref(false)
const loadingMore = ref(false)
const favoriteBusyId = ref(null)
const photos = ref([])
const albumMeta = ref(null)
const photoPage = ref(0)
const totalPhotos = ref(0)
let loadVersion = 0

const previewImages = computed(() => photos.value.map(item => originalImageUrl(item.imagePath)))
const editableAlbum = computed(() => !!albumMeta.value?.editable)
const hasMorePhotos = computed(() => photos.value.length < totalPhotos.value)
const albumTitle = computed(() => albumMeta.value?.name || fallbackSystemTitle())
const albumDescription = computed(() => {
  if (route.params.systemKey) return '默认相册按日记图片动态生成，不会复制图片。'
  return albumMeta.value?.description || '照片只从当前相册索引中移除，不会删除原图。'
})

const fallbackSystemTitle = () => {
  const systemKey = route.params.systemKey
  if (systemKey === 'all') return '所有图片'
  if (systemKey === 'favorites') return '收藏照片'
  if (String(systemKey || '').startsWith('year-')) return `${String(systemKey).replace('year-', '')} 年`
  return '相册详情'
}

const systemAlbumKey = () => {
  if (route.params.systemKey === 'all') return 'all'
  if (route.params.systemKey === 'favorites') return 'favorites'
  return `year:${String(route.params.systemKey).replace('year-', '')}`
}

const findAlbumMeta = async () => {
  const response = await albumApi.getGroups()
  const groups = response.data || []
  if (route.params.systemKey) {
    const expectedKey = route.params.systemKey === 'all'
      ? 'all'
      : route.params.systemKey === 'favorites'
        ? 'favorites'
        : `year:${String(route.params.systemKey).replace('year-', '')}`
    for (const group of groups) {
      const album = group.albums?.find(item => item.systemKey === expectedKey)
      if (album) return album
    }
    return { name: fallbackSystemTitle(), editable: false }
  }
  const albumId = Number(route.params.albumId)
  for (const group of groups) {
    const album = group.albums?.find(item => item.albumId === albumId)
    if (album) return album
  }
  return null
}

const requestPhotoPage = (page, options = {}) => {
  const params = { page, size: PHOTO_PAGE_SIZE }
  return route.params.systemKey
    ? albumApi.getSystemPhotoPage(systemAlbumKey(), params, options)
    : albumApi.getAlbumPhotoPage(route.params.albumId, params, options)
}

const loadAlbumPhotos = async ({ append = false, force = false } = {}) => {
  if (append && (loadingPhotos.value || loadingMore.value || !hasMorePhotos.value)) return
  const requestVersion = append ? loadVersion : ++loadVersion
  const nextPage = append ? photoPage.value + 1 : 0
  if (append) {
    loadingMore.value = true
  } else {
    loadingPhotos.value = true
    photos.value = []
    totalPhotos.value = 0
    photoPage.value = 0
  }
  try {
    let response
    if (!append) {
      const [meta, photoResponse] = await Promise.all([
        findAlbumMeta(),
        requestPhotoPage(nextPage, { force })
      ])
      if (requestVersion !== loadVersion) return
      albumMeta.value = meta
      response = photoResponse
    } else {
      response = await requestPhotoPage(nextPage, { force })
    }
    if (requestVersion !== loadVersion) return

    const content = response.data?.content || []
    if (append) {
      const existingIds = new Set(photos.value.map(photo => photo.imageId))
      photos.value = photos.value.concat(content.filter(photo => !existingIds.has(photo.imageId)))
    } else {
      photos.value = content
    }
    totalPhotos.value = response.data?.totalElements || 0
    photoPage.value = response.data?.pageNumber ?? nextPage
  } finally {
    if (requestVersion === loadVersion) {
      loadingPhotos.value = false
      loadingMore.value = false
    }
  }
}

const loadMorePhotos = () => loadAlbumPhotos({ append: true })

const toggleFavorite = async (photo) => {
  if (favoriteBusyId.value) return
  favoriteBusyId.value = photo.imageId
  try {
    if (photo.favorite) {
      await photoApi.unfavorite(photo.imageId)
      photo.favorite = false
      if (route.params.systemKey === 'favorites') {
        await loadAlbumPhotos({ force: true })
      }
      ElMessage.success('已取消收藏')
    } else {
      const response = await photoApi.favorite(photo.imageId)
      Object.assign(photo, response.data)
      ElMessage.success('已收藏')
    }
  } finally {
    favoriteBusyId.value = null
  }
}

const removePhoto = async (imageId) => {
  if (!editableAlbum.value || !route.params.albumId) return
  await albumApi.removeAlbumPhoto(route.params.albumId, imageId)
  await loadAlbumPhotos({ force: true })
  ElMessage.success('已移出相册')
}

onMounted(loadAlbumPhotos)
watch(() => route.fullPath, () => loadAlbumPhotos())
</script>

<style src="./styles/AlbumDetail.scss" scoped lang="scss"></style>
