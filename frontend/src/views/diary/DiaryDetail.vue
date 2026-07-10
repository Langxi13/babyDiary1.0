<template>
  <div class="page-shell">
    <main class="page-container detail-page" v-loading="loading">
      <div class="page-header">
        <el-button text @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="actions">
          <el-button @click="router.push('/diaries')">列表</el-button>
          <el-button type="primary" @click="router.push(`/diaries/${diaryId}/edit`)">
            <el-icon><Edit /></el-icon>
            编辑
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && !diary" description="日记不存在" />

      <article v-if="diary" class="detail-panel">
        <header class="detail-head">
          <div class="meta-row">
            <span>{{ formatChineseDate(diary.date) }}</span>
            <el-tag v-if="diary.moodKey" size="small" :color="moodColor(diary.moodKey)" effect="dark">
              {{ moodLabel(diary.moodKey) }}
            </el-tag>
          </div>
          <h1>{{ diary.title }}</h1>
          <div class="tag-row" v-if="diary.tags?.length">
            <el-tag v-for="tag in diary.tags" :key="tag.tagId" size="small" effect="plain" :color="tag.color">
              {{ tag.name }}
            </el-tag>
          </div>
        </header>

        <section class="content-section">
          <div v-if="diary.contentFormat === 'html'" class="rich-content" v-html="diary.content"></div>
          <p v-else class="plain-content">{{ diary.content }}</p>
        </section>

        <section v-if="diary.imagePathList?.length" class="image-grid">
          <el-image
            v-for="(img, index) in diary.imagePathList"
            :key="img"
            :src="thumbnailImageUrl(img)"
            :preview-src-list="diary.imagePathList.map(originalImageUrl)"
            :initial-index="index"
            :preview-teleported="true"
            fit="cover"
            class="detail-image"
            lazy
          />
        </section>

        <footer class="detail-foot">
          <span>创建于 {{ formatChineseDateTime(diary.createdAt) }}</span>
        </footer>
      </article>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import { diaryApi } from '@/api/diary'
import { moodColor, moodLabel } from '@/utils/diaryMeta'
import { formatChineseDate, formatChineseDateTime } from '@/utils/dateDisplay'
import { originalImageUrl, thumbnailImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/image/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'

const route = useRoute()
const router = useRouter()
const diaryId = computed(() => route.params.id)
const loading = ref(false)
const diary = ref(null)

const loadDiary = async () => {
  loading.value = true
  try {
    const response = await diaryApi.getDiary(diaryId.value)
    diary.value = response.data || null
  } catch (error) {
    diary.value = null
    if (!error?.message) {
      ElMessage.error('日记加载失败')
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDiary)
</script>

<style scoped lang="scss">
.page-shell {
  min-height: 100vh;
  background: #f6f3f0;
}

.detail-page {
  max-width: 980px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.actions {
  display: flex;
  gap: 8px;
}

.detail-panel {
  background: #fff;
  border: 1px solid #eadeda;
  border-radius: 8px;
  padding: 30px;
}

.detail-head {
  padding-bottom: 18px;
  border-bottom: 1px solid #eee4e1;

  h1 {
    margin-top: 10px;
    font-size: 32px;
    color: #2f2b28;
  }
}

.meta-row,
.tag-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-row {
  color: #8a817b;
  font-size: 14px;
}

.tag-row {
  margin-top: 12px;
}

.content-section {
  padding: 24px 0;
  color: #3d3734;
  font-size: 16px;
  line-height: 1.85;
}

.plain-content {
  white-space: pre-wrap;
}

.rich-content {
  :deep(p) {
    margin: 0 0 12px;
  }

  :deep(blockquote) {
    margin: 12px 0;
    border-left: 3px solid #3aa69b;
    padding-left: 12px;
    color: #66716d;
  }

  :deep(ul),
  :deep(ol) {
    margin-left: 24px;
  }
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.detail-image {
  width: 100%;
  height: 180px;
  border-radius: 8px;
}

.detail-foot {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid #eee4e1;
  color: #8a817b;
  font-size: 13px;
}

@media (max-width: 768px) {
  .page-header {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
    gap: 10px;
  }

  .actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;

    :deep(.el-button) {
      margin-left: 0;
    }
  }

  .detail-panel {
    padding: 18px;
    border-radius: 22px;
  }

  .detail-head h1 {
    font-size: 24px;
    line-height: 1.25;
  }

  .content-section {
    margin-top: 18px;
    font-size: 16px;
    line-height: 1.78;
  }

  .image-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .detail-image {
    height: auto;
    aspect-ratio: 1;
    border-radius: 16px;
  }
}
</style>
