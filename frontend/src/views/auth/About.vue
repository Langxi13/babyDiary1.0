<template>
  <div class="page-shell about-page-shell">
    <main class="page-container about-page">
      <header class="about-heading">
        <img src="/app-icon.png" alt="" />
        <div>
          <span>应用信息</span>
          <h1>Baby Diary</h1>
          <p>查看当前客户端、服务器兼容版本和 Android 更新状态。</p>
        </div>
      </header>

      <div class="about-surface">
        <section class="version-section" aria-labelledby="version-heading">
          <div class="section-heading">
            <div class="section-heading-icon"><el-icon><Cellphone /></el-icon></div>
            <div>
              <h2 id="version-heading">版本信息</h2>
              <p>版本号来自当前实际安装包，构建号用于判断能否覆盖升级。</p>
            </div>
          </div>

          <dl class="version-list">
            <div>
              <dt>客户端</dt>
              <dd>{{ clientVersionLabel }}</dd>
            </div>
            <div>
              <dt>运行平台</dt>
              <dd>{{ platformLabel }}</dd>
            </div>
            <div>
              <dt>服务器版本</dt>
              <dd>{{ updateStore.bootstrap?.serverVersion || '读取中' }}</dd>
            </div>
            <div>
              <dt>接口版本</dt>
              <dd>{{ updateStore.bootstrap?.apiVersion ? `V${updateStore.bootstrap.apiVersion}` : '读取中' }}</dd>
            </div>
            <div v-if="nativeApp">
              <dt>当前服务器</dt>
              <dd class="server-origin">{{ serverOrigin }}</dd>
            </div>
          </dl>
        </section>

        <section class="update-section" aria-labelledby="update-heading">
          <div class="section-heading update-heading-row">
            <div class="section-heading-copy">
              <div class="section-heading-icon"><el-icon><Refresh /></el-icon></div>
              <div>
                <h2 id="update-heading">应用更新</h2>
                <p>应用只接受服务器发布的 HTTPS 或同源更新地址。</p>
              </div>
            </div>
            <el-button :loading="updateStore.checking" @click="checkNow">
              <el-icon><Refresh /></el-icon>
              检查更新
            </el-button>
          </div>

          <div :class="['update-status', updateStatus.tone]" role="status">
            <el-icon class="update-status-icon">
              <WarningFilled v-if="updateStatus.tone === 'warning' || updateStatus.tone === 'danger'" />
              <CircleCheckFilled v-else />
            </el-icon>
            <div>
              <strong>{{ updateStatus.title }}</strong>
              <p>{{ updateStatus.description }}</p>
            </div>
          </div>

          <div v-if="updateStore.updateAvailable" class="release-details">
            <div class="release-title-row">
              <div>
                <span>可用版本</span>
                <strong>{{ updateStore.manifest.latestVersionName }}</strong>
              </div>
              <el-tag v-if="updateStore.updateRequired" type="danger" effect="plain">需要更新</el-tag>
              <el-tag v-else type="success" effect="plain">可选更新</el-tag>
            </div>
            <p class="release-notes">{{ updateStore.manifest.releaseNotes || '该版本没有附加更新说明。' }}</p>
            <div v-if="updateStore.manifest.sha256" class="checksum-row">
              <div>
                <span>APK SHA-256</span>
                <code>{{ updateStore.manifest.sha256 }}</code>
              </div>
              <el-button text circle title="复制校验值" aria-label="复制 APK SHA-256" @click="copyChecksum">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </div>
            <div class="update-actions">
              <div class="update-action-buttons">
                <el-button type="primary" :loading="openingUpdate" @click="installUpdate">
                  <el-icon><Download /></el-icon>
                  {{ updateStore.manifest.distribution === 'PLAY' ? '前往应用商店' : '下载并安装' }}
                </el-button>
                <el-button v-if="updateStore.manifest.distribution === 'DIRECT'" @click="copyDownloadUrl">
                  <el-icon><CopyDocument /></el-icon>
                  复制下载地址
                </el-button>
              </div>
              <p v-if="updateStore.manifest.distribution === 'DIRECT'">下载完成后由 Android 系统确认安装，应用无法静默替换自身。</p>
            </div>
          </div>
        </section>
      </div>

      <p class="about-footnote">更新安装仍由系统校验应用签名；服务器地址和账号信息不会写入安装包。</p>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { Cellphone, CircleCheckFilled, CopyDocument, Download, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { useAppUpdateStore } from '@/stores/appUpdate'
import { copyText } from '@/utils/copyText'
import { trustedUpdateUrl } from '@/platform/appRelease'
import { getServerOrigin, isNativeApp } from '@/platform/runtimeConfig'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'

const updateStore = useAppUpdateStore()
const nativeApp = isNativeApp()
const serverOrigin = getServerOrigin()
const openingUpdate = ref(false)

const clientVersionLabel = computed(() => {
  const info = updateStore.clientInfo
  if (!info) return '读取中'
  return info.build > 0 ? `${info.version}（构建 ${info.build}）` : info.version
})

const platformLabel = computed(() => ({
  android: 'Android 原生应用',
  ios: 'iOS 原生应用',
  web: '网页应用'
})[updateStore.clientInfo?.platform] || '读取中')

const updateStatus = computed(() => {
  if (updateStore.checking && !updateStore.checked) {
    return { tone: 'neutral', title: '正在检查更新', description: '正在读取服务器发布信息。' }
  }
  if (updateStore.error) {
    return { tone: 'warning', title: '暂时无法检查更新', description: updateStore.error }
  }
  if (!nativeApp) {
    return { tone: 'neutral', title: '网页版本自动部署', description: '刷新页面即可使用服务器已经部署的最新网页版本。' }
  }
  if (updateStore.clientInfo?.platform !== 'android') {
    return { tone: 'neutral', title: 'iOS 发布渠道尚未启用', description: '后续将通过 TestFlight 或 App Store 提供更新。' }
  }
  if (!updateStore.manifest?.enabled) {
    return { tone: 'neutral', title: '服务器未配置更新源', description: '当前版本仍可继续使用，可由管理员手动安装签名安装包。' }
  }
  if (updateStore.updateRequired) {
    return { tone: 'danger', title: '需要更新应用', description: '当前版本低于服务器支持范围，请尽快完成覆盖升级。' }
  }
  if (updateStore.updateAvailable) {
    return { tone: 'warning', title: '发现新版本', description: '可以查看更新说明后下载安装。' }
  }
  return { tone: 'success', title: '当前已是最新版本', description: `当前安装版本为 ${clientVersionLabel.value}。` }
})

const checkNow = async () => {
  await updateStore.check(true)
  if (!updateStore.error) ElMessage.success('更新信息已刷新')
}

const installUpdate = async () => {
  if (openingUpdate.value) return
  openingUpdate.value = true
  try {
    await updateStore.openUpdate()
  } catch (error) {
    ElMessage.error(error.message || '无法打开更新地址')
  } finally {
    openingUpdate.value = false
  }
}

const copyChecksum = async () => {
  const copied = await copyText(updateStore.manifest?.sha256 || '')
  ElMessage[copied ? 'success' : 'warning'](copied ? '校验值已复制' : '复制失败')
}

const copyDownloadUrl = async () => {
  try {
    const url = trustedUpdateUrl(updateStore.manifest?.downloadUrl)
    const copied = await copyText(url)
    ElMessage[copied ? 'success' : 'warning'](copied ? '下载地址已复制' : '复制失败')
  } catch (error) {
    ElMessage.error(error.message || '更新地址不可用')
  }
}

onMounted(() => updateStore.check())
</script>

<style scoped lang="scss">
.about-page-shell {
  min-height: 100vh;
  background: #f6f3f0;
}

.about-page {
  max-width: 920px;
  padding-top: 34px;
  padding-bottom: 42px;
}

.about-heading {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;

  img {
    width: 74px;
    height: 74px;
    flex: 0 0 auto;
    border-radius: 16px;
    box-shadow: 0 10px 24px rgba(71, 51, 42, 0.13);
  }

  span {
    color: #2f8f83;
    font-size: 12px;
    font-weight: 700;
  }

  h1 {
    margin-top: 3px;
    color: #302824;
    font-size: 28px;
    line-height: 1.2;
  }

  p {
    margin-top: 7px;
    color: #6f625b;
    line-height: 1.55;
  }
}

.about-surface {
  overflow: hidden;
  border: 1px solid #e7dcd5;
  border-radius: 8px;
  background: #fff;
}

.version-section,
.update-section {
  padding: 24px;
}

.update-section {
  border-top: 1px solid #eee5df;
}

.section-heading,
.section-heading-copy {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 13px;

  h2 {
    color: #302824;
    font-size: 19px;
    line-height: 1.3;
  }

  p {
    margin-top: 4px;
    color: #776a63;
    font-size: 13px;
    line-height: 1.55;
  }
}

.section-heading-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eaf5f2;
  color: #2f8f83;
  font-size: 19px;
}

.update-heading-row {
  justify-content: space-between;
  gap: 16px;

  > .el-button {
    flex: 0 0 auto;
    margin: 0;
  }
}

.version-list {
  margin-top: 20px;

  > div {
    min-height: 47px;
    padding: 10px 0;
    border-top: 1px solid #f0e9e4;
    display: grid;
    grid-template-columns: minmax(112px, 0.35fr) minmax(0, 1fr);
    align-items: center;
    gap: 16px;
  }

  dt {
    color: #82756d;
    font-size: 13px;
  }

  dd {
    min-width: 0;
    color: #3f3631;
    font-size: 14px;
    overflow-wrap: anywhere;
  }
}

.update-status {
  margin-top: 20px;
  padding: 14px;
  border: 1px solid #dce7e3;
  border-radius: 8px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  background: #f6faf8;

  &.warning { border-color: #ead3a7; background: #fff9ed; }
  &.danger { border-color: #e5b4bd; background: #fff2f4; }
  &.success { border-color: #acd1bf; background: #f0f8f4; }

  .update-status-icon {
    width: 30px;
    height: 30px;
    color: #2f8f83;
    font-size: 20px;
  }

  &.warning .update-status-icon { color: #9a671e; }
  &.danger .update-status-icon { color: #b4233f; }

  strong { color: #3c332f; font-size: 14px; }
  p { margin-top: 3px; color: #71655e; font-size: 13px; line-height: 1.55; }
}

.release-details {
  margin-top: 16px;
  padding-top: 18px;
  border-top: 1px solid #eee5df;
}

.release-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;

  > div { min-width: 0; display: grid; gap: 3px; }
  span { color: #82756d; font-size: 12px; }
  strong { color: #302824; font-size: 20px; overflow-wrap: anywhere; }
}

.release-notes {
  margin-top: 13px;
  color: #5f534d;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-line;
}

.checksum-row {
  margin-top: 15px;
  padding: 11px 12px;
  border: 1px solid #ece2dc;
  border-radius: 8px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px;
  align-items: center;
  gap: 10px;
  background: #faf8f6;

  > div { min-width: 0; display: grid; gap: 4px; }
  span { color: #82756d; font-size: 11px; }
  code { overflow: hidden; color: #4f4641; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
}

.update-actions {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 14px;

  p { color: #80736c; font-size: 12px; line-height: 1.5; }
}

.update-action-buttons {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 9px;

  > .el-button { margin: 0; }
}

.about-footnote {
  margin: 16px 4px 0;
  color: #82766f;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .about-page { padding: 18px 14px 28px; }
  .about-heading { gap: 14px; margin-bottom: 18px; }
  .about-heading img { width: 60px; height: 60px; border-radius: 13px; }
  .about-heading h1 { font-size: 23px; }
  .about-heading p { font-size: 13px; }
  .version-section, .update-section { padding: 18px 16px; }
  .update-heading-row { align-items: stretch; flex-direction: column; }
  .update-heading-row > .el-button { width: 100%; }
  .version-list > div { grid-template-columns: 92px minmax(0, 1fr); gap: 10px; }
  .update-actions { align-items: stretch; flex-direction: column; }
  .update-action-buttons { align-items: stretch; flex-direction: column; }
  .update-action-buttons > .el-button { width: 100%; min-height: 44px; }
}

@media (max-width: 380px) {
  .about-heading { align-items: flex-start; }
  .about-heading img { width: 52px; height: 52px; }
  .version-list > div { grid-template-columns: 1fr; gap: 4px; }
  .release-title-row { align-items: flex-start; flex-direction: column; }
}
</style>
