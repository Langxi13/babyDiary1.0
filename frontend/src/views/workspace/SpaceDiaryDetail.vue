<template>
  <div class="space-detail-page">
    <main class="space-detail-container" v-loading="loading">
      <header class="detail-toolbar">
        <el-button text @click="router.push('/spaces')"><el-icon><ArrowLeft /></el-icon>共同空间</el-button>
        <div>
          <el-button @click="historyOpen = true; loadRevisions()"><el-icon><Clock /></el-icon>历史</el-button>
          <el-button @click="shareOpen = true"><el-icon><Share /></el-icon>分享</el-button>
          <el-button type="primary" @click="editorOpen = true"><el-icon><EditPen /></el-icon>编辑</el-button>
        </div>
      </header>

      <el-empty v-if="!loading && !diary" description="日记不存在" />

      <article v-if="diary" class="space-detail-article">
        <header class="article-head">
          <div class="article-meta">
            <time>{{ formatChineseDate(diary.diaryDate) }}</time>
            <span v-if="diary.mood">{{ moodEmoji(diary.mood) }}</span>
            <span>{{ diary.visibility === 'PRIVATE' ? '仅自己' : '共同可见' }}</span>
            <el-icon v-if="diary.locked"><Lock /></el-icon>
          </div>
          <h1>{{ diary.title }}</h1>
          <div v-if="diary.tags?.length" class="article-tags">
            <span v-for="tag in diary.tags" :key="tag.id"><i :style="{ background: tag.color }" />{{ tag.name }}</span>
          </div>
        </header>

        <section class="article-content">
          <div v-if="diary.contentHtml" v-html="diary.contentHtml" />
          <p v-else>{{ diary.contentText }}</p>
        </section>

        <diary-media-gallery :media="diary.media" />

        <footer class="article-footer">
          <span>更新于 {{ formatChineseDateTime(diary.updatedAt || diary.createdAt) }}</span>
          <span>版本 {{ diary.version }}</span>
        </footer>
      </article>

      <section v-if="diary" class="interaction-band">
        <div class="reaction-row">
          <button
            v-for="emoji in reactionChoices"
            :key="emoji"
            type="button"
            :class="{ active: reactionFor(emoji)?.reactedByMe }"
            @click="toggleReaction(emoji)"
          >
            {{ emoji }}<span v-if="reactionFor(emoji)?.count">{{ reactionFor(emoji).count }}</span>
          </button>
        </div>
        <div class="comment-composer">
          <el-input v-model="commentText" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" maxlength="2000" placeholder="写下回应" />
          <el-button type="primary" :disabled="!commentText.trim()" :loading="commenting" @click="addComment">发送</el-button>
        </div>
        <div class="comment-list">
          <article v-for="comment in comments" :key="comment.id">
            <el-avatar :size="34" :src="comment.avatarMedia?.contentUrl">{{ comment.username?.slice(0, 1) }}</el-avatar>
            <div class="comment-body">
              <header>
                <div><strong>{{ comment.username }}</strong><time>{{ formatChineseDateTime(comment.createdAt) }}</time></div>
                <div v-if="isOwnComment(comment)" class="comment-actions">
                  <el-button :icon="EditPen" circle text size="small" title="编辑评论" @click="beginCommentEdit(comment)" />
                  <el-button :icon="Delete" circle text size="small" title="删除评论" @click="removeComment(comment)" />
                </div>
              </header>
              <div v-if="editingCommentId === comment.id" class="comment-editor">
                <el-input v-model="editingCommentText" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" maxlength="2000" />
                <div><el-button size="small" @click="cancelCommentEdit">取消</el-button><el-button size="small" type="primary" :loading="savingComment" @click="saveComment(comment)">保存</el-button></div>
              </div>
              <p v-else>{{ comment.content }}</p>
            </div>
          </article>
          <el-empty v-if="!comments.length" description="还没有回应" :image-size="60" />
        </div>
      </section>
    </main>

    <space-diary-editor
      v-if="diary"
      v-model="editorOpen"
      :space-id="spaceId"
      :diary="diary"
      :tags="tags"
      :templates="templates"
      @saved="saved => { diary = saved; loadDiary() }"
    />

    <el-drawer v-model="historyOpen" title="版本历史" size="min(480px, 100%)">
      <div v-loading="loadingHistory" class="revision-list">
        <article v-for="revision in revisions" :key="revision.id">
          <div><strong>版本 {{ revision.version }}</strong><span>{{ revision.editorName }} · {{ formatChineseDateTime(revision.createdAt) }}</span></div>
          <el-button size="small" @click="restoreRevision(revision)">恢复</el-button>
        </article>
        <el-empty v-if="!loadingHistory && !revisions.length" description="暂无历史版本" />
      </div>
    </el-drawer>

    <el-dialog v-model="shareOpen" title="私密分享" width="min(520px, 92vw)" @open="loadShares" @closed="shareUrl = ''">
      <el-form label-position="top" class="share-form">
        <el-form-item label="有效期">
          <el-select v-model="shareForm.expiresInHours">
            <el-option label="1小时" :value="1" /><el-option label="24小时" :value="24" /><el-option label="7天" :value="168" /><el-option label="30天" :value="720" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问密码">
          <el-input v-model="shareForm.password" type="password" show-password maxlength="64" placeholder="可选" />
        </el-form-item>
        <el-form-item label="最多浏览次数">
          <el-input-number v-model="shareForm.maxViews" :min="1" :max="10000" placeholder="不限" />
        </el-form-item>
      </el-form>
      <div v-if="shareUrl" class="share-result"><el-input :model-value="shareUrl" readonly /><el-button :icon="CopyDocument" circle title="复制链接" @click="copyShare" /></div>
      <section class="active-shares">
        <header><h3>活动中的分享</h3><span>{{ activeShares.length }} 个</span></header>
        <div v-loading="loadingShares" class="active-share-list">
          <article v-for="share in activeShares" :key="share.id">
            <div>
              <strong>{{ share.passwordProtected ? '已设置访问密码' : '无需访问密码' }}</strong>
              <span>{{ formatChineseDateTime(share.expiresAt) }} 到期 · {{ share.viewCount || 0 }}{{ share.maxViews ? ` / ${share.maxViews}` : '' }} 次浏览</span>
            </div>
            <el-button :icon="Delete" circle text type="danger" title="撤销分享" @click="revokeShare(share)" />
          </article>
          <el-empty v-if="!loadingShares && !activeShares.length" description="暂无活动分享" :image-size="54" />
        </div>
      </section>
      <template #footer>
        <el-button @click="shareOpen = false">关闭</el-button>
        <el-button type="primary" :loading="sharing" @click="createShare">生成链接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ArrowLeft, Clock, CopyDocument, Delete, EditPen, Lock, Share } from '@element-plus/icons-vue'
import SpaceDiaryEditor from '@/components/workspace/SpaceDiaryEditor.vue'
import { workspaceApi } from '@/api/workspace'
import { diaryApi } from '@/api/diary'
import { tagApi } from '@/api/experience'
import DiaryMediaGallery from '@/components/diary/DiaryMediaGallery.vue'
import { useAuthStore } from '@/stores/auth'
import { withStepUpRetry } from '@/utils/stepUp'
import { formatChineseDate, formatChineseDateTime } from '@/utils/dateDisplay'
import { copyText } from '@/utils/copyText'
import 'element-plus/es/components/avatar/style/css.mjs'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/drawer/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/input-number/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/message-box/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const spaceId = route.params.spaceId
const diaryId = route.params.diaryId
const diary = ref(null)
const loading = ref(false)
const comments = ref([])
const reactions = ref([])
const commentText = ref('')
const commenting = ref(false)
const editingCommentId = ref('')
const editingCommentText = ref('')
const savingComment = ref(false)
const editorOpen = ref(false)
const historyOpen = ref(false)
const loadingHistory = ref(false)
const revisions = ref([])
const tags = ref([])
const templates = ref([])
const shareOpen = ref(false)
const sharing = ref(false)
const shareUrl = ref('')
const generatedShareId = ref('')
const activeShares = ref([])
const loadingShares = ref(false)
const shareForm = reactive({ expiresInHours: 24, password: '', maxViews: null })
const reactionChoices = ['❤️', '👍', '🥰', '😂', '🎉', '🤗']

const loadDiary = async () => {
  loading.value = true
  try {
    diary.value = await diaryApi.getDiary(spaceId, diaryId, { force: true })
    await Promise.all([loadComments(), loadReactions()])
  } finally { loading.value = false }
}

const loadComments = async () => {
  comments.value = await withStepUpRetry(token => diaryApi.comments(spaceId, diaryId, token)) || []
}

const loadReactions = async () => {
  reactions.value = await withStepUpRetry(token => diaryApi.reactions(spaceId, diaryId, token)) || []
}

const addComment = async () => {
  if (!commentText.value.trim()) return
  commenting.value = true
  try {
    await withStepUpRetry(token => diaryApi.addComment(spaceId, diaryId, commentText.value.trim(), token))
    commentText.value = ''
    await loadComments()
  } finally { commenting.value = false }
}

const isOwnComment = comment => String(comment.authorId || '') === String(authStore.userInfo?.id || '')
const beginCommentEdit = comment => {
  editingCommentId.value = comment.id
  editingCommentText.value = comment.content
}
const cancelCommentEdit = () => {
  editingCommentId.value = ''
  editingCommentText.value = ''
}
const saveComment = async comment => {
  const content = editingCommentText.value.trim()
  if (!content) return
  savingComment.value = true
  try {
    await withStepUpRetry(token => diaryApi.updateComment(spaceId, diaryId, comment.id, content, token))
    cancelCommentEdit()
    await loadComments()
  } finally { savingComment.value = false }
}
const removeComment = async comment => {
  await ElMessageBox.confirm('删除后无法恢复。', '删除评论', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  await withStepUpRetry(token => diaryApi.removeComment(spaceId, diaryId, comment.id, token))
  if (editingCommentId.value === comment.id) cancelCommentEdit()
  await loadComments()
}

const reactionFor = emoji => reactions.value.find(item => item.emoji === emoji)
const toggleReaction = async emoji => {
  const current = reactionFor(emoji)
  await withStepUpRetry(token => diaryApi.setReaction(spaceId, diaryId, emoji, !current?.reactedByMe, token))
  await loadReactions()
}

const loadRevisions = async () => {
  loadingHistory.value = true
  try {
    revisions.value = await withStepUpRetry(token => diaryApi.revisions(spaceId, diaryId, token)) || []
  } finally { loadingHistory.value = false }
}

const restoreRevision = async revision => {
  await withStepUpRetry(token => diaryApi.restoreRevision(spaceId, diaryId, revision.id, diary.value.version, token))
  historyOpen.value = false
  await loadDiary()
}

const createShare = async () => {
  sharing.value = true
  try {
    const payload = { ...shareForm, password: shareForm.password || null }
    const result = await withStepUpRetry(token => workspaceApi.shares.create(spaceId, diaryId, payload, token))
    shareUrl.value = `${window.location.origin}${result.sharePath}`
    generatedShareId.value = result.id
    await loadShares()
  } finally { sharing.value = false }
}

const loadShares = async () => {
  loadingShares.value = true
  try {
    activeShares.value = await withStepUpRetry(token => workspaceApi.shares.list(spaceId, diaryId, token)) || []
  } finally { loadingShares.value = false }
}

const revokeShare = async share => {
  await ElMessageBox.confirm('该链接撤销后将立即无法访问。', '撤销分享', { confirmButtonText: '撤销', cancelButtonText: '取消', type: 'warning' })
  await workspaceApi.shares.revoke(share.id)
  if (generatedShareId.value === share.id) {
    shareUrl.value = ''
    generatedShareId.value = ''
  }
  await loadShares()
}

const copyShare = async () => {
  if (await copyText(shareUrl.value)) ElMessage.success('分享链接已复制')
  else ElMessage.warning('复制失败，请长按链接手动复制')
}

const moodEmoji = key => ({ happy: '😊', calm: '😌', loved: '🥰', excited: '🤩', tired: '😴', sad: '🥺' }[key] || '🙂')

onMounted(async () => {
  await authStore.getUserInfo()
  const [tagResponse, templateResponse] = await Promise.all([
    tagApi.list(spaceId), workspaceApi.templates.list(spaceId)
  ])
  tags.value = tagResponse || []
  templates.value = templateResponse || []
  await loadDiary()
})
</script>

<style scoped lang="scss">
.space-detail-page { min-height: 100vh; background: #f6f3f0; }
.space-detail-container { width: min(980px, calc(100% - 32px)); margin: 0 auto; padding: 24px 0 56px; }
.detail-toolbar { min-height: 42px; margin-bottom: 14px; display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.detail-toolbar > div { display: flex; gap: 8px; }
.space-detail-article, .interaction-band { border: 1px solid #e6ddd8; border-radius: 8px; background: #fff; }
.space-detail-article { padding: 30px; }
.article-head { padding-bottom: 20px; border-bottom: 1px solid #eee5e0; }
.article-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; color: #887d77; font-size: 13px; }
.article-meta span + span:before { content: '·'; margin-right: 9px; color: #c8bdb7; }
.article-head h1 { margin: 11px 0 0; font-size: 32px; line-height: 1.25; }
.article-tags { margin-top: 13px; display: flex; flex-wrap: wrap; gap: 7px; }
.article-tags span { padding: 4px 8px; border-radius: 6px; background: #f6f2ef; color: #6e645f; font-size: 12px; }
.article-tags i { width: 7px; height: 7px; margin-right: 5px; border-radius: 50%; display: inline-block; }
.article-content { padding: 26px 0; color: #3e3835; font-size: 16px; line-height: 1.9; }
.article-content > p { white-space: pre-wrap; }
.article-content :deep(p) { margin: 0 0 13px; }.article-content :deep(blockquote) { margin: 14px 0; padding-left: 14px; border-left: 3px solid #419689; color: #65716d; }
.legacy-images { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
.legacy-images :deep(.el-image) { width: 100%; aspect-ratio: 1; border-radius: 8px; }
.rich-media { margin-top: 14px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.rich-media figure { min-width: 0; margin: 0; border: 1px solid #e8dfda; border-radius: 8px; overflow: hidden; background: #faf8f6; }
.rich-media :deep(.el-image), .rich-media video { width: 100%; aspect-ratio: 16/10; display: block; object-fit: cover; }
.rich-media audio { width: calc(100% - 24px); margin: 18px 12px; }
.rich-media figcaption { padding: 10px 12px; display: flex; flex-direction: column; gap: 3px; font-size: 13px; }.rich-media figcaption small { color: #8d827c; }
.article-footer { margin-top: 24px; padding-top: 14px; border-top: 1px solid #eee5e0; display: flex; justify-content: space-between; color: #8b807a; font-size: 12px; }
.interaction-band { margin-top: 14px; padding: 20px; }
.reaction-row { display: flex; flex-wrap: wrap; gap: 8px; }
.reaction-row button { min-width: 46px; height: 38px; padding: 0 10px; border: 1px solid #e4d9d3; border-radius: 8px; background: #fff; cursor: pointer; }.reaction-row button.active { border-color: #bd756b; background: #fff1ee; }.reaction-row span { margin-left: 5px; font-size: 11px; color: #756a64; }
.comment-composer { margin: 18px 0; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: end; }
.comment-list { display: grid; gap: 13px; }
.comment-list article { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 10px; }
.comment-body { min-width: 0; }
.comment-list header { min-height: 28px; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.comment-list header > div:first-child { min-width: 0; display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; }
.comment-list time { color: #99908a; font-size: 11px; }
.comment-actions { flex: none; display: flex; gap: 2px; }
.comment-actions :deep(.el-button + .el-button) { margin-left: 0; }
.comment-list p { margin: 5px 0 0; color: #5d5551; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.comment-editor { margin-top: 7px; display: grid; gap: 8px; }
.comment-editor > div { display: flex; justify-content: flex-end; gap: 6px; }
.comment-editor :deep(.el-button + .el-button) { margin-left: 0; }
.revision-list { display: grid; gap: 8px; }.revision-list article { min-height: 62px; padding: 10px 12px; border: 1px solid #e7ddd8; border-radius: 8px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }.revision-list article > div { min-width: 0; display: flex; flex-direction: column; gap: 4px; }.revision-list span { color: #8c817a; font-size: 12px; }
.share-form { display: grid; gap: 16px; }.share-form :deep(.el-form-item) { margin-bottom: 0; }.share-form :deep(.el-select), .share-form :deep(.el-input-number) { width: 100%; }
.share-result { margin-top: 18px; display: grid; grid-template-columns: minmax(0, 1fr) 40px; gap: 8px; }
.active-shares { margin-top: 22px; padding-top: 18px; border-top: 1px solid #eee5e0; }
.active-shares > header { margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.active-shares h3 { margin: 0; font-size: 15px; }
.active-shares > header span { color: #91857f; font-size: 12px; }
.active-share-list { min-height: 70px; display: grid; gap: 8px; }
.active-share-list article { min-width: 0; padding: 10px 8px 10px 12px; border: 1px solid #e8dfda; border-radius: 8px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.active-share-list article > div { min-width: 0; display: grid; gap: 3px; }
.active-share-list strong { color: #4e4642; font-size: 13px; }
.active-share-list span { color: #8d827c; font-size: 11px; line-height: 1.5; }

@media (max-width: 768px) {
  .space-detail-container { width: 100%; padding: 0 12px 28px; }
  .detail-toolbar { justify-content: flex-end; }.detail-toolbar > :first-child { display: none; }.detail-toolbar > div { width: 100%; display: grid; grid-template-columns: 1fr 1fr 1fr; }.detail-toolbar :deep(.el-button) { width: 100%; margin: 0; }
  .space-detail-article { padding: 17px 15px; }
  .article-head h1 { font-size: 25px; }
  .article-content { padding: 20px 0; font-size: 16px; line-height: 1.8; }
  .legacy-images, .rich-media { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .interaction-band { padding: 15px; }
  .comment-composer { grid-template-columns: 1fr; }.comment-composer :deep(.el-button) { width: 100%; }
  .comment-actions { opacity: 1; }
  .article-footer { flex-direction: column; gap: 4px; }
  .active-share-list article { align-items: flex-start; }
}
</style>
