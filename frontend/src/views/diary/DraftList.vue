<template>
  <div class="page-shell">
    <main class="page-container">
      <div class="page-title-row">
        <div>
          <h1>草稿</h1>
          <p>继续未完成的记录，或者清理不需要的草稿</p>
        </div>
        <el-button class="new-diary-button" type="primary" @click="router.push('/diaries/create')">
          <el-icon><Plus /></el-icon>
          新日记
        </el-button>
      </div>

      <section v-loading="loading" class="draft-list">
        <el-empty v-if="drafts.length === 0" description="暂无草稿" />
        <article v-for="draft in drafts" :key="draft.draftId" class="draft-card">
          <button class="draft-main" @click="openDraft(draft)">
            <span class="draft-type">{{ draftTypeLabel(draft) }}</span>
            <strong>{{ draft.title || '未命名草稿' }}</strong>
            <p>{{ previewText(draft) }}</p>
            <span class="draft-time">{{ formatChineseDateTime(draft.updatedAt) }}</span>
          </button>
          <el-popconfirm
            title="确定删除这份草稿吗？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="deleteDraft(draft)"
          >
            <template #reference>
              <el-button class="draft-delete-button" type="danger" text :loading="deletingId === draft.draftId" :disabled="!!deletingId" aria-label="删除草稿">
                <el-icon><Delete /></el-icon>
                <span class="action-label">删除</span>
              </el-button>
            </template>
          </el-popconfirm>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElPopconfirm } from 'element-plus/es/components/popconfirm/index.mjs'
import { Delete, Plus } from '@element-plus/icons-vue'
import { draftApi } from '@/api/experience'
import { stripHtml } from '@/utils/diaryMeta'
import { formatChineseDateTime } from '@/utils/dateDisplay'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/popconfirm/style/css.mjs'

const router = useRouter()
const loading = ref(false)
const drafts = ref([])
const deletingId = ref(null)

const draftTypeLabel = (draft) => String(draft.draftKey || '').startsWith('create') ? '新日记草稿' : '编辑草稿'
const previewText = (draft) => {
  const text = draft.contentFormat === 'html' ? stripHtml(draft.content) : draft.content
  return text?.slice(0, 120) || '暂无正文'
}
const draftDiaryId = (draft) => {
  if (draft.diaryId) return draft.diaryId
  const match = String(draft.draftKey || '').match(/^edit-(\d+)$/)
  return match ? Number(match[1]) : null
}

const loadDrafts = async () => {
  loading.value = true
  try {
    const response = await draftApi.list()
    drafts.value = response.data || []
  } finally {
    loading.value = false
  }
}

const openDraft = (draft) => {
  if (String(draft.draftKey || '').startsWith('create')) {
    router.push({
      path: '/diaries/create',
      query: { draftKey: draft.draftKey }
    })
    return
  }
  const diaryId = draftDiaryId(draft)
  if (diaryId) {
    router.push({
      path: `/diaries/${diaryId}/edit`,
      query: { draftKey: draft.draftKey }
    })
  } else {
    ElMessage.warning('草稿缺少关联日记，无法继续编辑')
  }
}

const deleteDraft = async (draft) => {
  if (deletingId.value) return
  deletingId.value = draft.draftId
  try {
    await draftApi.remove(draft.draftId)
    drafts.value = drafts.value.filter(item => item.draftId !== draft.draftId)
    ElMessage.success('草稿已删除')
  } finally {
    deletingId.value = null
  }
}

onMounted(loadDrafts)
</script>

<style scoped lang="scss">
.page-shell {
  min-height: 100vh;
  background: #f6f3f0;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;

  h1 {
    font-size: 26px;
    color: #2f2b28;
  }

  p {
    margin-top: 6px;
    color: #6f6661;
  }
}

.draft-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 180px;
}

.draft-card {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: #fff;
  border: 1px solid #eadeda;
  border-radius: 8px;
}

.draft-main {
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;

  strong,
  p,
  span {
    display: block;
  }

  strong {
    margin-top: 6px;
    color: #2f2b28;
    font-size: 18px;
  }

  p {
    margin-top: 8px;
    color: #5f5a55;
    line-height: 1.6;
  }
}

.draft-type {
  color: #b14f64;
  font-size: 12px;
  font-weight: 700;
}

.draft-time {
  margin-top: 8px;
  color: #6f6661;
  font-size: 12px;
}

@media (max-width: 768px) {
  .page-title-row {
    gap: 12px;

    .new-diary-button {
      display: none;
    }
  }

  .draft-list {
    gap: 12px;
  }

  .draft-card {
    grid-template-columns: minmax(0, 1fr) 42px;
    align-items: center;
    gap: 8px;
    padding: 14px;
    border-radius: 8px;
  }

  .draft-main {
    min-width: 0;
  }

  .draft-main strong,
  .draft-main span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .draft-main p {
    display: -webkit-box;
    max-height: 44px;
    overflow: hidden;
    font-size: 13px;
    line-height: 1.55;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .draft-card > :deep(.el-button) {
    width: 42px;
    min-width: 42px;
    height: 42px;
    padding: 0;
    margin-left: 0;
  }

  .action-label {
    display: none;
  }
}
</style>
