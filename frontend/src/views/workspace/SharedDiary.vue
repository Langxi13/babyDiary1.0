<template>
  <div class="shared-page">
    <main class="shared-container">
      <header><el-icon><Notebook /></el-icon><strong>Baby Diary</strong></header>
      <section v-if="!diary" class="share-unlock">
        <h1>一段与你分享的回忆</h1>
        <el-input v-model="password" type="password" show-password placeholder="访问密码（如有）" @keyup.enter="openShare" />
        <el-button type="primary" :loading="loading" @click="openShare">打开日记</el-button>
      </section>
      <article v-else class="shared-article">
        <time>{{ formatChineseDate(diary.date) }}</time><h1>{{ diary.title }}</h1>
        <div v-if="diary.contentFormat === 'html'" class="shared-content" v-html="diary.content" /><p v-else class="shared-content plain">{{ diary.content }}</p>
        <div v-if="diary.imagePathList?.length" class="shared-images"><el-image v-for="image in diary.imagePathList" :key="image" :src="originalImageUrl(image)" fit="cover" /></div>
        <div v-if="diary.media?.length" class="shared-media"><template v-for="media in diary.media" :key="media.assetId"><img v-if="media.mediaType === 'IMAGE'" :src="media.thumbnailUrl || media.contentUrl" /><audio v-else-if="media.mediaType === 'AUDIO'" controls :src="media.contentUrl" /><video v-else controls playsinline :poster="media.posterUrl" :src="media.transcodedUrl || media.contentUrl" /></template></div>
      </article>
    </main>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { Notebook } from '@element-plus/icons-vue'
import { workspaceApi } from '@/api/workspace'
import { formatChineseDate } from '@/utils/dateDisplay'
import { originalImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/button/style/css.mjs'; import 'element-plus/es/components/icon/style/css.mjs'; import 'element-plus/es/components/image/style/css.mjs'; import 'element-plus/es/components/input/style/css.mjs'
const route = useRoute(); const password = ref(''); const diary = ref(null); const loading = ref(false)
const openShare = async () => { loading.value = true; try { diary.value = (await workspaceApi.shares.open(route.params.token, password.value || null)).data } finally { loading.value = false } }
</script>
<style scoped lang="scss">
.shared-page { min-height: 100vh; padding: 28px 16px; background: #f6f3f0; color: #312c29; }.shared-container { width: min(820px, 100%); margin: 0 auto; }.shared-container > header { margin-bottom: 24px; display: flex; align-items: center; gap: 8px; color: #356f68; font-size: 18px; }.share-unlock, .shared-article { border: 1px solid #e4dad5; border-radius: 8px; background: #fff; }.share-unlock { max-width: 460px; margin: 12vh auto 0; padding: 28px; display: grid; gap: 16px; }.share-unlock h1 { margin: 0 0 4px; font-size: 25px; }.shared-article { padding: 34px; }.shared-article time { color: #897e78; font-size: 13px; }.shared-article h1 { margin: 10px 0 24px; font-size: 32px; }.shared-content { line-height: 1.85; }.shared-content.plain { white-space: pre-wrap; }.shared-images, .shared-media { margin-top: 22px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }.shared-images :deep(.el-image), .shared-media img, .shared-media video { width: 100%; aspect-ratio: 1; border-radius: 8px; object-fit: cover; }.shared-media audio { width: 100%; }
@media(max-width:600px){.shared-page{padding:18px 10px}.share-unlock{margin-top:8vh;padding:22px 18px}.shared-article{padding:22px 16px}.shared-article h1{font-size:26px}.shared-images,.shared-media{gap:7px}}
</style>
