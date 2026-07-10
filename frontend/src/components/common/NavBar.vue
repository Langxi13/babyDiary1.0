<template>
  <div class="navbar">
    <div class="navbar-content">
      <div class="navbar-brand" @click="router.push('/')">
        <el-icon :size="28" color="#409eff"><Notebook /></el-icon>
        <span class="brand-text">Baby Diary</span>
      </div>
      
      <div class="navbar-menu">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          :ellipsis="false"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/" @mouseenter="preload('/')">
            <el-icon><HomeFilled /></el-icon>
            首页
          </el-menu-item>
          <el-menu-item index="/diaries" @mouseenter="preload('/diaries')">
            <el-icon><Document /></el-icon>
            日记
          </el-menu-item>
          <el-menu-item index="/timeline" @mouseenter="preload('/timeline')">
            <el-icon><Clock /></el-icon>
            时间轴
          </el-menu-item>
          <el-menu-item index="/calendar" @mouseenter="preload('/calendar')">
            <el-icon><Calendar /></el-icon>
            日历
          </el-menu-item>
          <el-menu-item index="/anniversaries" @mouseenter="preload('/anniversaries')">
            <el-icon><Star /></el-icon>
            纪念日
          </el-menu-item>
          <el-menu-item index="/album" @mouseenter="preload('/album')">
            <el-icon><Picture /></el-icon>
            相册
          </el-menu-item>
          <el-menu-item index="/ai-reports" @mouseenter="preload('/ai-reports')">
            <el-icon><MagicStick /></el-icon>
            AI 报告
          </el-menu-item>
          <el-menu-item index="/drafts" @mouseenter="preload('/drafts')">
            <el-icon><Tickets /></el-icon>
            草稿
          </el-menu-item>
          <el-menu-item index="/diaries/create" @mouseenter="preload('/diaries/create')">
            <el-icon><Edit /></el-icon>
            写日记
          </el-menu-item>
        </el-menu>
      </div>
      
      <div class="navbar-user">
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="36" :src="avatarUrl">
              {{ username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="username">{{ username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item class="profile-dropdown-item">
                <router-link class="dropdown-route-link" to="/profile">
                  <el-icon><User /></el-icon>
                  个人信息
                </router-link>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      
      <div class="mobile-menu">
        <el-dropdown trigger="click" @command="handleCommand">
          <el-button :icon="Menu" circle />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/">首页</el-dropdown-item>
              <el-dropdown-item command="/diaries">日记</el-dropdown-item>
              <el-dropdown-item command="/timeline">时间轴</el-dropdown-item>
              <el-dropdown-item command="/calendar">日历</el-dropdown-item>
              <el-dropdown-item command="/anniversaries">纪念日</el-dropdown-item>
              <el-dropdown-item command="/album">相册</el-dropdown-item>
              <el-dropdown-item command="/ai-reports">AI 报告</el-dropdown-item>
              <el-dropdown-item command="/drafts">草稿</el-dropdown-item>
              <el-dropdown-item command="/diaries/create">写日记</el-dropdown-item>
              <el-dropdown-item class="profile-dropdown-item">
                <router-link class="dropdown-route-link" to="/profile">个人信息</router-link>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElMenu, ElMenuItem } from 'element-plus/es/components/menu/index.mjs'
import { Notebook, HomeFilled, Document, Edit, ArrowDown, User, SwitchButton, Menu, Clock, Calendar, Star, Picture, Tickets, MagicStick } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { preloadRouteComponent } from '@/router'
import { originalImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/avatar/style/css.mjs'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/dropdown/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/menu/style/css.mjs'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeMenu = computed(() => {
  if (route.path === '/diaries/create') return '/diaries/create'
  if (route.path === '/drafts') return '/drafts'
  if (route.path === '/ai-reports') return '/ai-reports'
  if (route.path.startsWith('/diaries')) return '/diaries'
  if (route.path.startsWith('/album')) return '/album'
  return route.path
})
const username = computed(() => authStore.username)
const avatarUrl = computed(() => originalImageUrl(authStore.userInfo?.avatarPath))
const preload = (path) => preloadRouteComponent(path)

const handleMenuSelect = (index) => {
  router.push(index)
}

const handleCommand = (command) => {
  if (command === 'logout') {
    authStore.logout()
  } else {
    router.push(command)
  }
}
</script>

<style scoped lang="scss">
.navbar {
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-content {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 clamp(20px, 3vw, 36px);
  height: 60px;
  display: grid;
  grid-template-columns: minmax(160px, 180px) minmax(0, 1fr) minmax(160px, 180px);
  align-items: center;
  column-gap: 28px;
}

.navbar-brand {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  justify-self: start;
  gap: 8px;
  cursor: pointer;
  
  .brand-text {
    font-size: 20px;
    font-weight: 600;
    color: #333;
    white-space: nowrap;
  }
}

.navbar-menu {
  flex: 1 1 auto;
  min-width: 0;
  width: 100%;
  justify-self: center;
  overflow: hidden;

  :deep(.el-menu) {
    justify-content: center;
    border-bottom: none;
    background: transparent;
  }
  
  :deep(.el-menu-item) {
    height: 60px;
    line-height: 60px;
    padding: 0 12px;
  }
}

.navbar-user {
  flex-shrink: 0;
  justify-self: end;

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 8px 12px;
    border-radius: 8px;
    transition: background 0.3s;
    
    &:hover {
      background: #f5f7fa;
    }
    
    .username {
      max-width: 92px;
      overflow: hidden;
      font-size: 14px;
      color: #333;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.mobile-menu {
  display: none;
  justify-self: end;
}

:deep(.profile-dropdown-item) {
  padding: 0;
}

.dropdown-route-link {
  width: 100%;
  min-height: 34px;
  padding: 5px 16px;
  color: inherit;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1280px) {
  .navbar-content {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .navbar-menu {
    display: none;
  }
  
  .navbar-user {
    display: none;
  }
  
  .mobile-menu {
    display: block;
  }
}
</style>
