<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <button type="button" class="space-switcher" :class="{ compact }" aria-label="切换日记空间">
      <el-icon><Connection /></el-icon>
      <span>{{ workspaceStore.activeSpace?.name || '日记空间' }}</span>
      <el-icon class="chevron"><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu class="space-menu">
        <el-dropdown-item
          v-for="space in workspaceStore.spaces"
          :key="space.spaceId"
          :command="`space:${space.spaceId}`"
          :class="{ selected: space.spaceId === workspaceStore.activeSpaceId }"
        >
          <el-icon><UserFilled v-if="space.type === 'PERSONAL'" /><Connection v-else /></el-icon>
          <span class="space-name">{{ space.name }}</span>
          <small>{{ space.memberCount }} 人</small>
        </el-dropdown-item>
        <el-dropdown-item divided command="manage">
          <el-icon><Setting /></el-icon>
          管理空间
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ArrowDown, Connection, Setting, UserFilled } from '@element-plus/icons-vue'
import { useWorkspaceStore } from '@/stores/workspace'
import 'element-plus/es/components/dropdown/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'

defineProps({ compact: Boolean })
const router = useRouter()
const workspaceStore = useWorkspaceStore()

const handleCommand = command => {
  if (command === 'manage') {
    router.push('/spaces/settings')
    return
  }
  if (command.startsWith('space:')) {
    workspaceStore.selectSpace(command.slice(6))
    router.push('/spaces')
  }
}

onMounted(() => workspaceStore.initialize())
</script>

<style scoped lang="scss">
.space-switcher {
  max-width: 210px;
  height: 38px;
  padding: 0 10px;
  border: 1px solid #e5d7d2;
  border-radius: 8px;
  background: #fff;
  color: #514945;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
  }

  .chevron {
    flex: 0 0 auto;
    color: #958b86;
  }

  &:hover {
    border-color: #c98f83;
    background: #fff8f5;
  }

  &.compact {
    max-width: 176px;
  }
}

:global(.space-menu .el-dropdown-menu__item) {
  min-width: 240px;
  gap: 8px;
}

:global(.space-menu .space-name) {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

:global(.space-menu small) {
  color: #99908b;
}

:global(.space-menu .selected) {
  color: #a95062;
  background: #fff3f4;
}
</style>
