<template>
  <div class="auth-page">
    <main class="auth-card">
      <header class="auth-header">
        <img src="/app-icon.png" alt="" class="auth-logo" />
        <div>
          <span>私人日记与回忆相册</span>
          <h1>Baby Diary</h1>
          <p>欢迎回来，继续记录你们的日常。</p>
        </div>
      </header>
      
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form login-form">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            autocomplete="username"
            autocapitalize="none"
            :spellcheck="false"
          />
        </el-form-item>
        
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <div class="auth-help-row">
          <button type="button" class="auth-link-button" @click="recoveryOpen = true">
            忘记密码或使用恢复码
          </button>
        </div>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            :disabled="loading"
            class="auth-submit login-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
        
        <div class="auth-footer login-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="register-link">立即注册</router-link>
        </div>
      </el-form>
    </main>

    <el-dialog
      v-model="recoveryOpen"
      :title="resetToken ? '设置新密码' : '找回账户'"
      width="min(500px, calc(100vw - 28px))"
      append-to-body
      class="account-recovery-dialog"
      @closed="handleRecoveryClosed"
    >
      <div v-if="resetToken" class="recovery-panel">
        <p class="recovery-copy">验证链接有效，请设置新的登录密码。</p>
        <label class="recovery-field">
          <span>新密码</span>
          <el-input v-model="recoveryForm.newPassword" type="password" show-password autocomplete="new-password" />
        </label>
        <label class="recovery-field">
          <span>确认新密码</span>
          <el-input v-model="recoveryForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </label>
      </div>

      <el-tabs v-else v-model="recoveryMode" stretch class="recovery-tabs">
        <el-tab-pane label="邮箱重置" name="email">
          <div class="recovery-panel">
            <p class="recovery-copy">输入已验证邮箱。无论账户是否存在，系统都会返回相同结果。</p>
            <label class="recovery-field">
              <span>邮箱</span>
              <el-input v-model="recoveryForm.email" type="email" autocomplete="email" placeholder="name@example.com" />
            </label>
          </div>
        </el-tab-pane>
        <el-tab-pane label="恢复码" name="code">
          <div class="recovery-panel">
            <p class="recovery-copy">使用个人信息页生成的一次性恢复码重置密码。</p>
            <label class="recovery-field">
              <span>用户名</span>
              <el-input v-model="recoveryForm.username" autocomplete="username" />
            </label>
            <label class="recovery-field">
              <span>恢复码</span>
              <el-input v-model="recoveryForm.recoveryCode" autocomplete="one-time-code" placeholder="XXXX-XXXX-XXXX-XXXX" />
            </label>
            <label class="recovery-field">
              <span>新密码</span>
              <el-input v-model="recoveryForm.newPassword" type="password" show-password autocomplete="new-password" />
            </label>
            <label class="recovery-field">
              <span>确认新密码</span>
              <el-input v-model="recoveryForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </label>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="recovery-actions">
          <el-button @click="recoveryOpen = false">取消</el-button>
          <el-button type="primary" :loading="recoveryLoading" @click="submitRecovery">
            {{ recoverySubmitLabel }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/tabs/style/css.mjs'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const recoveryOpen = ref(false)
const recoveryMode = ref('email')
const recoveryLoading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const recoveryForm = reactive({
  email: '',
  username: '',
  recoveryCode: '',
  newPassword: '',
  confirmPassword: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const firstQueryValue = (value) => Array.isArray(value) ? value[0] : value
const tokenFromHash = () => new URLSearchParams((route.hash || '').replace(/^#/, '')).get('resetToken') || ''
const resetToken = computed(() => firstQueryValue(route.query.resetToken) || tokenFromHash())
const recoverySubmitLabel = computed(() => resetToken.value
  ? '重置密码'
  : recoveryMode.value === 'email' ? '发送重置邮件' : '使用恢复码重置')

const loginRedirect = () => {
  const redirect = firstQueryValue(route.query.redirect)
  return redirect?.startsWith('/') ? redirect : '/'
}

const handleLogin = async () => {
  if (loading.value) return
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  loading.value = true
  try {
    const result = await authStore.login(form)
    if (result.success) {
      ElMessage.success('登录成功')
      router.push(loginRedirect())
    } else {
      ElMessage.error(result.message || '登录失败')
    }
  } catch (error) {
    ElMessage.error('登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const validNewPassword = () => {
  if (recoveryForm.newPassword.length < 6 || recoveryForm.newPassword.length > 64) {
    ElMessage.warning('新密码长度需在6到64位之间')
    return false
  }
  if (recoveryForm.newPassword !== recoveryForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return false
  }
  return true
}

const submitRecovery = async () => {
  if (recoveryLoading.value) return
  recoveryLoading.value = true
  try {
    const { authApi } = await import('@/api/auth')
    if (resetToken.value) {
      if (!validNewPassword()) return
      await authApi.resetPassword(resetToken.value, recoveryForm.newPassword)
      authStore.clearAuth()
      ElMessage.success('密码已重置，请使用新密码登录')
      await clearResetToken()
      recoveryOpen.value = false
      return
    }
    if (recoveryMode.value === 'email') {
      const email = recoveryForm.email.trim()
      if (!/^\S+@\S+\.\S+$/.test(email)) {
        ElMessage.warning('请输入有效邮箱')
        return
      }
      await authApi.requestPasswordReset(email)
      ElMessage.success('如果邮箱已验证，重置邮件将很快送达')
      recoveryOpen.value = false
      return
    }
    if (!recoveryForm.username.trim() || !recoveryForm.recoveryCode.trim()) {
      ElMessage.warning('请填写用户名和恢复码')
      return
    }
    if (!validNewPassword()) return
    await authApi.recoverPassword(
      recoveryForm.username.trim(),
      recoveryForm.recoveryCode.trim(),
      recoveryForm.newPassword
    )
    authStore.clearAuth()
    ElMessage.success('密码已重置，请使用新密码登录')
    recoveryOpen.value = false
  } finally {
    recoveryLoading.value = false
  }
}

const clearResetToken = async () => {
  if (!resetToken.value) return
  const query = { ...route.query }
  delete query.resetToken
  await router.replace({ path: '/login', query, hash: '' })
}

const handleRecoveryClosed = () => {
  clearResetToken()
}

watch(resetToken, token => {
  if (token) recoveryOpen.value = true
}, { immediate: true })
</script>

<style src="./styles/Auth.scss" scoped lang="scss"></style>
