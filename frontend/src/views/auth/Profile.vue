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
              <strong>{{ avatarUploading ? '上传中...' : '拖拽图片到这里' }}</strong>
              <span>点击更换头像</span>
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
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import { Lock, Upload } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { formatChineseDate } from '@/utils/dateDisplay'
import { originalImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/avatar/style/css.mjs'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/upload/style/css.mjs'

const authStore = useAuthStore()
const router = useRouter()
const avatarUploading = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref(null)

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
</script>

<style scoped lang="scss">
.page-shell {
  min-height: 100vh;
  background: #f6f3f0;
}

.profile-page {
  max-width: 980px;
}

.page-title-row {
  margin-bottom: 18px;

  h1 {
    line-height: 1.25;
    font-size: 26px;
    color: #2f2b28;
  }

  p {
    margin-top: 6px;
    line-height: 1.45;
    color: #77706a;
  }
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.profile-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #eadeda;
  border-radius: 8px;
  padding: 22px;

  h2 {
    margin-bottom: 18px;
    color: #2f2b28;
    font-size: 20px;
    line-height: 1.3;
  }
}

.avatar-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;

  :deep(.el-upload) {
    width: 100%;
    max-width: 100%;
  }
}

.avatar-upload-card {
  width: 100%;

  :deep(.el-upload-dragger) {
    width: 100%;
    padding: 0;
    border: 1px dashed #d7b5aa;
    border-radius: 8px;
    overflow: hidden;
    background: #fff8f4;
  }
}

.avatar-upload-trigger {
  min-height: 108px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #8a6259;
  text-align: center;
  line-height: 1.4;
  overflow-wrap: anywhere;

  .el-icon {
    color: #b76d61;
    font-size: 26px;
  }

  strong {
    color: #5a4039;
    font-size: 14px;
  }

  span {
    font-size: 13px;
  }

  &.uploading {
    opacity: 0.72;
  }
}

.profile-copy {
  min-width: 0;
  width: 100%;
  overflow-wrap: anywhere;

  h2 {
    margin-bottom: 4px;
    line-height: 1.25;
  }

  span {
    color: #8a817b;
    font-size: 13px;
    line-height: 1.45;
  }
}

.password-form {
  display: grid;
  gap: 18px;
  min-width: 0;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    min-height: auto;
    padding-bottom: 8px;
    line-height: 1.35;
  }

  :deep(.el-input) {
    width: 100%;
    min-width: 0;
  }

  :deep(.el-button) {
    width: fit-content;
    max-width: 100%;
    white-space: normal;
  }
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

@media (max-width: 768px) {
  .page-title-row {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .profile-grid {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .profile-panel {
    padding: 18px;
    border-radius: 20px;
  }

  .avatar-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-copy {
    width: 100%;
  }

  .password-form {
    gap: 16px;

    :deep(.el-button) {
      width: 100%;
    }
  }
}

@media (max-width: 420px) {
  .profile-panel {
    padding: 16px;
  }
}
</style>
