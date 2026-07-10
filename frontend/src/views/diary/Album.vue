<template>
  <div class="page-shell">
    <main class="page-container album-page">
      <div class="page-title-row">
        <div>
          <h1>相册</h1>
          <p>按默认年份、AI 整理和自建相册管理日记图片</p>
        </div>
        <div class="title-actions">
          <el-button @click="openGroupEditor">
            <el-icon><FolderAdd /></el-icon>
            新建相册组
          </el-button>
          <el-button type="primary" @click="showAiPanel = !showAiPanel">
            <el-icon><MagicStick /></el-icon>
            AI 整理
          </el-button>
        </div>
      </div>

      <section v-if="showAiPanel" class="ai-panel">
        <div>
          <h2>AI 推荐相册</h2>
          <p>选择日记时间段，AI 会按日记内容推荐新相册或合并到已有 AI 相册。</p>
        </div>
        <div class="ai-form">
          <div class="ai-date-fields">
            <el-date-picker
              v-model="aiForm.startDate"
              type="date"
              placeholder="开始日期"
              format="YYYY年MM月DD日"
              value-format="YYYY-MM-DD"
            />
            <el-date-picker
              v-model="aiForm.endDate"
              type="date"
              placeholder="结束日期"
              format="YYYY年MM月DD日"
              value-format="YYYY-MM-DD"
            />
          </div>
          <el-input
            v-model="aiForm.prompt"
            type="textarea"
            :rows="3"
            placeholder="可选：比如重点整理欧洲旅游、生日、成长瞬间"
          />
          <el-button type="primary" :loading="generatingProposal" @click="generateProposal">
            <el-icon><MagicStick /></el-icon>
            生成推荐
          </el-button>
        </div>
      </section>

      <section v-if="currentProposal" class="proposal-panel">
        <div class="proposal-head">
          <div>
            <h2>待确认的 AI 相册</h2>
            <p>{{ formatChineseDateRange(currentProposal.startDate, currentProposal.endDate) }} · 确认后才会写入正式相册</p>
          </div>
          <div class="proposal-actions">
            <el-button @click="discardProposal">放弃</el-button>
            <el-button type="primary" :loading="confirmingProposal" @click="confirmProposal">确认保存</el-button>
          </div>
        </div>

        <div class="proposal-list">
          <article
            v-for="(album, albumIndex) in proposalAlbums"
            :key="albumIndex"
            class="proposal-album"
            :class="{ discarded: album.discarded }"
          >
            <div class="proposal-edit">
              <el-input v-model="album.title" placeholder="相册名" />
              <el-input v-model="album.description" placeholder="相册描述" />
              <el-tag v-if="album.mode === 'MERGE'" type="warning">合并到 {{ album.targetAlbumName }}</el-tag>
              <el-tag v-else type="success">新建 AI 相册</el-tag>
              <el-button text type="danger" @click="discardProposalAlbum(album)">取消这个推荐</el-button>
            </div>
            <div class="proposal-photos">
              <div v-for="photo in album.photos" :key="photo.imageId" class="proposal-photo">
                <img :src="thumbnailImageUrl(photo.imagePath)" alt="" loading="lazy" decoding="async" />
                <button @click="removeProposalPhoto(album, photo.imageId)">移除</button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-loading="loadingGroups" class="album-browser">
        <aside class="album-group-list">
          <button
            v-for="group in albumGroups"
            :key="groupKey(group)"
            class="group-item"
            :data-editable="group.editable"
            :class="{ active: groupKey(group) === groupKey(selectedGroup) }"
            @click="selectGroup(group)"
          >
            <span>{{ group.name }}</span>
            <small>{{ group.type === 'SYSTEM' ? '系统相册' : group.type === 'AI' ? 'AI 相册组' : '自建相册组' }}</small>
          </button>
        </aside>

        <section class="album-list">
          <div class="section-head">
            <div>
              <h2>{{ selectedGroup?.name || '相册组' }}</h2>
              <p>{{ selectedGroup?.editable ? '可编辑自建相册组' : '系统相册不可编辑' }}</p>
            </div>
            <div class="section-actions">
              <el-button v-if="selectedGroup?.editable" @click="openAlbumEditor()">
                <el-icon><Plus /></el-icon>
                新建相册
              </el-button>
              <el-button v-if="selectedGroup?.editable" text type="danger" @click="deleteGroup">删除组</el-button>
            </div>
          </div>

          <el-empty v-if="selectedAlbums.length === 0" description="暂无相册" />
          <div v-else class="album-card-grid">
            <article
              v-for="album in selectedAlbums"
              :key="albumKey(album)"
              class="album-card"
              @click="openAlbumDetail(album)"
            >
              <div class="album-cover" :class="{ empty: !album.coverImagePath }" :style="coverStyle(album)">
                <el-icon v-if="!album.coverImagePath"><Picture /></el-icon>
              </div>
              <div class="album-info">
                <strong>{{ formatSystemAlbumTitle(album) }}</strong>
                <span>{{ album.photoCount || 0 }} 张照片</span>
                <small>{{ album.editable ? '可编辑' : '系统相册' }}</small>
              </div>
              <div v-if="album.editable" class="album-card-actions" @click.stop>
                <el-button text @click="openAlbumEditor(album)">编辑</el-button>
                <el-button text type="danger" @click="deleteAlbum(album)">删除</el-button>
              </div>
            </article>
          </div>
        </section>
      </section>
    </main>

    <el-dialog v-model="groupDialogVisible" title="相册组" width="420px">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="groupForm.name" placeholder="例如：家庭旅行" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="albumDialogVisible" title="相册" width="460px">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="albumForm.name" placeholder="例如：欧洲旅行" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="albumForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="albumDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAlbum">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { FolderAdd, MagicStick, Picture, Plus } from '@element-plus/icons-vue'
import { albumApi } from '@/api/album'
import { formatLocalDate } from '@/utils/aiReportPeriod'
import { formatChineseDateRange, formatChineseMonth } from '@/utils/dateDisplay'
import { thumbnailImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'

const router = useRouter()
const loadingGroups = ref(false)
const generatingProposal = ref(false)
const confirmingProposal = ref(false)
const showAiPanel = ref(false)
const albumGroups = ref([])
const selectedGroup = ref(null)
const currentProposal = ref(null)
const proposalAlbums = ref([])
const groupDialogVisible = ref(false)
const albumDialogVisible = ref(false)
const editingAlbum = ref(null)

const today = formatLocalDate(new Date())
const aiForm = reactive({
  startDate: today,
  endDate: today,
  prompt: ''
})
const groupForm = reactive({ name: '' })
const albumForm = reactive({ name: '', description: '' })

const selectedAlbums = computed(() => selectedGroup.value?.albums || [])

const groupKey = (group) => group ? `${group.type}:${group.groupId || 'system'}` : ''
const albumKey = (album) => album ? `${album.systemKey || album.albumId}` : ''
const coverStyle = (album) => album.coverImagePath ? { backgroundImage: `url(${thumbnailImageUrl(album.coverImagePath)})` } : {}
const formatSystemAlbumTitle = (album) => {
  if (!album?.systemKey?.startsWith('year:')) return album?.name || ''
  return formatChineseMonth(`${album.systemKey.replace('year:', '')}-01`).replace('1月', '')
}

const loadGroups = async () => {
  loadingGroups.value = true
  try {
    const response = await albumApi.getGroups({ force: true })
    albumGroups.value = response.data || []
    if (!selectedGroup.value) {
      selectGroup(albumGroups.value[0])
    } else {
      const freshGroup = albumGroups.value.find(item => groupKey(item) === groupKey(selectedGroup.value))
      selectGroup(freshGroup || albumGroups.value[0])
    }
  } finally {
    loadingGroups.value = false
  }
}

const selectGroup = (group) => {
  selectedGroup.value = group || null
}

const openAlbumDetail = (album) => {
  if (!album) return
  if (album.systemKey === 'all') {
    router.push('/album/system/all')
    return
  }
  if (album.systemKey === 'favorites') {
    router.push('/album/system/favorites')
    return
  }
  if (album.systemKey?.startsWith('year:')) {
    router.push(`/album/system/year-${album.systemKey.replace('year:', '')}`)
    return
  }
  router.push(`/album/item/${album.albumId}`)
}

const generateProposal = async () => {
  if (!aiForm.startDate || !aiForm.endDate) {
    ElMessage.warning('请选择整理时间段')
    return
  }
  generatingProposal.value = true
  try {
    const response = await albumApi.generateProposal({
      startDate: aiForm.startDate,
      endDate: aiForm.endDate,
      prompt: aiForm.prompt
    })
    currentProposal.value = response.data
    proposalAlbums.value = response.data?.albums || []
    ElMessage.success('AI 推荐已生成，请确认')
  } finally {
    generatingProposal.value = false
  }
}

const removeProposalPhoto = (album, imageId) => {
  album.photos = album.photos.filter(photo => photo.imageId !== imageId)
  album.imageIds = album.imageIds.filter(id => id !== imageId)
}

const discardProposalAlbum = (album) => {
  album.discarded = true
}

const confirmProposal = async () => {
  if (!currentProposal.value) return
  confirmingProposal.value = true
  try {
    await albumApi.updateProposal(currentProposal.value.proposalId, {
      ...currentProposal.value,
      albums: proposalAlbums.value
    })
    await albumApi.confirmProposal(currentProposal.value.proposalId)
    currentProposal.value = null
    proposalAlbums.value = []
    await loadGroups()
    ElMessage.success('AI 相册已保存')
  } finally {
    confirmingProposal.value = false
  }
}

const discardProposal = async () => {
  if (currentProposal.value?.proposalId) {
    await albumApi.discardProposal(currentProposal.value.proposalId)
  }
  currentProposal.value = null
  proposalAlbums.value = []
}

const openGroupEditor = () => {
  groupForm.name = ''
  groupDialogVisible.value = true
}

const saveGroup = async () => {
  await albumApi.createGroup({ name: groupForm.name })
  groupDialogVisible.value = false
  await loadGroups()
}

const deleteGroup = async () => {
  if (!selectedGroup.value?.editable) return
  await albumApi.deleteGroup(selectedGroup.value.groupId)
  selectedGroup.value = null
  await loadGroups()
}

const openAlbumEditor = (album = null) => {
  editingAlbum.value = album
  albumForm.name = album?.name || ''
  albumForm.description = album?.description || ''
  albumDialogVisible.value = true
}

const saveAlbum = async () => {
  if (editingAlbum.value) {
    await albumApi.updateAlbum(editingAlbum.value.albumId, {
      groupId: selectedGroup.value.groupId,
      name: albumForm.name,
      description: albumForm.description
    })
  } else {
    await albumApi.createAlbum({
      groupId: selectedGroup.value.groupId,
      name: albumForm.name,
      description: albumForm.description
    })
  }
  albumDialogVisible.value = false
  await loadGroups()
}

const deleteAlbum = async (album) => {
  await albumApi.deleteAlbum(album.albumId)
  await loadGroups()
}

onMounted(loadGroups)
</script>

<style src="./styles/Album.scss" scoped lang="scss"></style>
