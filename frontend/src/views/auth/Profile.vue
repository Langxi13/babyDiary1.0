<template>
  <div class="page-shell">
    <main class="page-container profile-page">
      <div class="page-title-row">
        <div>
          <h1>个人信息</h1>
          <p>头像和登录密码</p>
        </div>
      </div>

      <section class="profile-grid">
        <div class="profile-panel avatar-panel">
          <el-avatar :size="88" :src="avatarUrl">
            {{ usernameInitial }}
          </el-avatar>
          <div class="profile-copy">
            <h2>{{ authStore.username }}</h2>
            <span>加入于 {{ joinedAt }}</span>
          </div>
          <el-upload
            class="avatar-upload-card"
            action="#"
            accept="image/*"
            :auto-upload="false"
            :show-file-list="false"
            :drag="true"
            :on-change="handleAvatarChange"
          >
            <div class="avatar-upload-trigger" :class="{ uploading: avatarUploading }">
              <el-icon><Upload /></el-icon>
              <strong>{{ avatarUploading ? '上传中...' : '更换头像' }}</strong>
              <span class="desktop-upload-copy">拖拽或点击选择图片</span>
              <span class="mobile-upload-copy">从相册选择图片</span>
            </div>
          </el-upload>
        </div>

        <div class="profile-panel">
          <h2>修改密码</h2>
          <el-form ref="passwordFormRef" class="password-form" :model="passwordForm" :rules="passwordRules" label-position="top">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-button type="primary" :loading="passwordSaving" @click="submitPassword">
              <el-icon><Lock /></el-icon>
              保存密码
            </el-button>
          </el-form>
        </div>

        <div class="profile-panel account-security-panel">
          <div class="security-heading">
            <div><h2>账户安全</h2><p>邮箱验证、恢复码与登录设备</p></div>
            <el-tag :class="['security-status', { verified: authStore.userInfo?.emailVerified }]">
              {{ authStore.userInfo?.emailVerified ? '邮箱已验证' : '邮箱未验证' }}
            </el-tag>
          </div>
          <div class="email-row">
            <el-input v-model="email" type="email" placeholder="name@example.com" />
            <el-button :loading="emailSaving" @click="saveEmail">保存邮箱</el-button>
            <el-button @click="generateRecoveryCodes">生成恢复码</el-button>
          </div>
          <div class="session-list">
            <article v-for="session in sessions" :key="session.publicId">
              <el-icon><Monitor /></el-icon>
              <div><strong>{{ session.deviceName }}</strong><span>{{ session.ipAddress }} · 最近使用 {{ formatChineseDateTime(session.lastSeenAt) }}</span></div>
              <el-tag v-if="session.current" class="current-session-tag" size="small">当前设备</el-tag>
              <el-button v-else text type="danger" @click="revokeSession(session)">退出</el-button>
            </article>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import { Lock, Monitor, Upload } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { formatChineseDate } from '@/utils/dateDisplay'
import { formatChineseDateTime } from '@/utils/dateDisplay'
import { originalImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/avatar/style/css.mjs'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/upload/style/css.mjs'

const authStore = useAuthStore()
const router = useRouter()
const avatarUploading = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref(null)
const email = ref(authStore.userInfo?.email || '')
const emailSaving = ref(false)
const sessions = ref([])

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需在6到64位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const avatarUrl = computed(() => originalImageUrl(authStore.userInfo?.avatarPath))
const usernameInitial = computed(() => authStore.username?.charAt(0)?.toUpperCase() || '')
const joinedAt = computed(() => formatChineseDate(authStore.userInfo?.createdAt))

const handleAvatarChange = async (uploadFile) => {
  if (!uploadFile.raw) return
  if (!uploadFile.raw.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return
  }
  avatarUploading.value = true
  try {
    await authStore.uploadAvatar(uploadFile.raw)
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

const submitPassword = async () => {
  if (passwordSaving.value || !passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch (error) {
    return
  }
  passwordSaving.value = true
  try {
    const response = await authStore.changePassword({ ...passwordForm })
    ElMessage.success('密码已修改，请重新登录')
    if (response.code === 200) {
      authStore.clearAuth()
      router.push('/login')
    }
  } finally {
    passwordSaving.value = false
  }
}

const loadSessions = async () => {
  const { authApi } = await import('@/api/auth')
  const response = await authApi.getSessions()
  sessions.value = response.data || []
}

const saveEmail = async () => {
  if (!email.value.trim()) return
  emailSaving.value = true
  try {
    const { authApi } = await import('@/api/auth')
    const response = await authApi.updateEmail({ email: email.value.trim() })
    ElMessage.success(response.message)
    await authStore.getUserInfo({ force: true })
  } finally { emailSaving.value = false }
}

const generateRecoveryCodes = async () => {
  const { value } = await import('element-plus/es/components/message-box/index.mjs').then(({ ElMessageBox }) => ElMessageBox.prompt(
    '恢复码只显示一次，请妥善保存。', '生成恢复码', { inputType: 'password', inputPlaceholder: '当前密码', confirmButtonText: '生成', cancelButtonText: '取消' }
  ))
  const { authApi } = await import('@/api/auth')
  const response = await authApi.recoveryCodes(value)
  await import('element-plus/es/components/message-box/index.mjs').then(({ ElMessageBox }) => ElMessageBox.alert(
    response.data.join('\n'), '账户恢复码', { confirmButtonText: '我已保存', customClass: 'recovery-code-dialog' }
  ))
}

const revokeSession = async session => {
  const { authApi } = await import('@/api/auth')
  await authApi.revokeSession(session.publicId)
  await loadSessions()
}

onMounted(async () => {
  const currentRoute = router.currentRoute.value
  const hashToken = new URLSearchParams((currentRoute.hash || '').replace(/^#/, '')).get('verifyEmail')
  const token = currentRoute.query.verifyEmail || hashToken
  if (token) {
    const { authApi } = await import('@/api/auth')
    await authApi.confirmEmail(token)
    ElMessage.success('邮箱验证成功')
    await authStore.getUserInfo({ force: true })
    router.replace('/profile')
  }
  email.value = authStore.userInfo?.email || ''
  await loadSessions()
})
</script>

<style src="./styles/Profile.scss" scoped lang="scss"></style>
