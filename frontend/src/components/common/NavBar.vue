<template>
  <div class="navbar">
    <div class="navbar-content">
      <div class="navbar-brand" @click="router.push('/')">
        <el-icon :size="28" color="var(--el-color-primary)"><Notebook /></el-icon>
        <span class="brand-text">Baby Diary</span>
      </div>
      
      <div class="navbar-menu">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          :ellipsis="false"
          @select="handleMenuSelect"
        >
          <el-menu-item
            v-for="item in desktopPrimaryNavigation"
            :key="item.id"
            :index="item.path"
            class="desktop-primary-item"
            @mouseenter="preload(item.path)"
          >
            <el-icon><component :is="navigationIcons[item.icon]" /></el-icon>
            {{ item.label }}
          </el-menu-item>
          <el-sub-menu index="desktop-more" :class="['desktop-more-menu', { 'is-route-active': moreMenuActive }]">
            <template #title>
              <el-icon><MoreFilled /></el-icon>
              更多
            </template>
            <el-menu-item
              v-for="item in desktopMoreNavigation"
              :key="item.id"
              :index="item.path"
              class="desktop-more-item"
              @mouseenter="preload(item.path)"
            >
              <el-icon><component :is="navigationIcons[item.icon]" /></el-icon>
              {{ item.label }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
      
      <div class="navbar-user">
        <space-switcher compact />
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
              <el-dropdown-item
                v-for="item in compactNavigation"
                :key="item.id"
                :command="item.path"
              >
                {{ item.label }}
              </el-dropdown-item>
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
import { ElMenu, ElMenuItem, ElSubMenu } from 'element-plus/es/components/menu/index.mjs'
import { Notebook, HomeFilled, Document, Edit, ArrowDown, User, SwitchButton, Menu, Clock, Calendar, Star, Picture, Tickets, MagicStick, Connection, MoreFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { preloadRouteComponent } from '@/router'
import { originalImageUrl } from '@/utils/imageUrl'
import { DESKTOP_COMPACT_NAVIGATION, DESKTOP_MORE_NAVIGATION, DESKTOP_PRIMARY_NAVIGATION } from '@/config/navigation'
import SpaceSwitcher from '@/components/common/SpaceSwitcher.vue'
import 'element-plus/es/components/avatar/style/css.mjs'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/dropdown/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/menu/style/css.mjs'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const desktopPrimaryNavigation = DESKTOP_PRIMARY_NAVIGATION
const desktopMoreNavigation = DESKTOP_MORE_NAVIGATION
const compactNavigation = DESKTOP_COMPACT_NAVIGATION
const navigationIcons = { HomeFilled, Document, Edit, Clock, Calendar, Star, Picture, Tickets, MagicStick, Connection }

const activeMenu = computed(() => {
  if (route.path === '/diaries/create') return '/diaries/create'
  if (route.path === '/drafts') return '/drafts'
  if (route.path === '/ai-reports') return '/ai-reports'
  if (route.path.startsWith('/diaries')) return '/diaries'
  if (route.path.startsWith('/album')) return '/album'
  if (route.path.startsWith('/spaces')) return '/spaces'
  return route.path
})
const username = computed(() => authStore.username)
const avatarUrl = computed(() => originalImageUrl(authStore.userInfo?.avatarPath))
const moreMenuActive = computed(() => desktopMoreNavigation.some(item => item.path === activeMenu.value))
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
  grid-template-columns: minmax(160px, 180px) minmax(0, 1fr) minmax(300px, 360px);
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
  overflow: visible;

  :deep(.el-menu) {
    justify-content: center;
    border-bottom: none;
    background: transparent;
  }
  
  :deep(.el-menu-item) {
    height: 60px;
    line-height: 60px;
    padding: 0 14px;
  }

  :deep(.el-sub-menu__title) {
    height: 60px;
    line-height: 60px;
    padding: 0 14px;
  }

  :deep(.el-sub-menu.is-route-active > .el-sub-menu__title) {
    color: var(--el-menu-active-color);
    border-bottom: 2px solid var(--el-menu-active-color);
  }
}

.navbar-user {
  flex-shrink: 0;
  justify-self: end;
  display: flex;
  align-items: center;
  gap: 10px;

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
