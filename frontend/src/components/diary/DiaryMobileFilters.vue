<template>
  <div class="mobile-filter-stack">
    <el-input
      v-model="keyword"
      class="mobile-search-input"
      placeholder="搜索标题或内容"
      clearable
      aria-label="搜索日记"
      @input="emit('keyword-input')"
      @clear="emit('filter')"
      @keyup.enter="emit('filter')"
    >
      <template #prefix><el-icon><Search /></el-icon></template>
    </el-input>

    <div class="mobile-filter-row" role="group" aria-label="日记筛选">
      <button
        type="button"
        class="mobile-filter-trigger"
        :class="{ active: activePanel === 'date' || hasDateFilter }"
        :aria-expanded="activePanel === 'date'"
        aria-controls="mobile-diary-filter-panel"
        @click="togglePanel('date')"
      >
        <span>{{ dateFilterLabel }}</span>
        <el-icon :class="{ expanded: activePanel === 'date' }"><ArrowDown /></el-icon>
      </button>
      <button
        type="button"
        class="mobile-filter-trigger"
        :class="{ active: activePanel === 'tag' || !!tagId }"
        :aria-expanded="activePanel === 'tag'"
        aria-controls="mobile-diary-filter-panel"
        @click="togglePanel('tag')"
      >
        <span>{{ tagFilterLabel }}</span>
        <el-icon :class="{ expanded: activePanel === 'tag' }"><ArrowDown /></el-icon>
      </button>
      <button
        type="button"
        class="mobile-filter-trigger"
        :class="{ active: activePanel === 'mood' || !!moodKey }"
        :aria-expanded="activePanel === 'mood'"
        aria-controls="mobile-diary-filter-panel"
        @click="togglePanel('mood')"
      >
        <span>{{ moodFilterLabel }}</span>
        <el-icon :class="{ expanded: activePanel === 'mood' }"><ArrowDown /></el-icon>
      </button>
      <button
        type="button"
        class="mobile-filter-reset"
        :disabled="!hasActiveFilters"
        aria-label="重置全部筛选"
        title="重置全部筛选"
        @click="resetFilters"
      >
        <el-icon><RefreshLeft /></el-icon>
      </button>
    </div>

    <transition name="mobile-filter-panel">
      <section
        v-if="activePanel"
        id="mobile-diary-filter-panel"
        class="mobile-filter-panel"
        @keydown.esc="activePanel = ''"
      >
        <template v-if="activePanel === 'date'">
          <div class="mobile-filter-panel-head">
            <strong>按日期筛选</strong>
            <span>可只选择开始或结束日期</span>
          </div>
          <div class="mobile-date-fields">
            <label>
              <span>开始日期</span>
              <el-date-picker
                v-model="startDate"
                type="date"
                placeholder="选择开始日期"
                format="YYYY年MM月DD日"
                value-format="YYYY-MM-DD"
              />
            </label>
            <label>
              <span>结束日期</span>
              <el-date-picker
                v-model="endDate"
                type="date"
                placeholder="选择结束日期"
                format="YYYY年MM月DD日"
                value-format="YYYY-MM-DD"
              />
            </label>
          </div>
          <div class="mobile-filter-panel-actions">
            <el-button @click="clearDateFilter">清除日期</el-button>
            <el-button type="primary" @click="applyDateFilter">应用日期</el-button>
          </div>
          <el-button
            class="mobile-export-action"
            :disabled="!startDate || !endDate"
            :loading="exporting"
            @click="emit('export')"
          >
            <el-icon><Download /></el-icon>
            导出该日期范围的图片
          </el-button>
        </template>

        <template v-else-if="activePanel === 'tag'">
          <div class="mobile-filter-panel-head">
            <strong>按标签筛选</strong>
            <span>选择后立即更新列表</span>
          </div>
          <div class="mobile-filter-choices tag-choices">
            <button type="button" :class="{ selected: !tagId }" @click="selectTag(null)">
              全部标签
            </button>
            <button
              v-for="tag in tags"
              :key="tag.tagId"
              type="button"
              :class="{ selected: tagId === tag.tagId }"
              @click="selectTag(tag.tagId)"
            >
              <i :style="{ backgroundColor: tag.color }" />
              <span>{{ tag.name }}</span>
            </button>
          </div>
        </template>

        <template v-else>
          <div class="mobile-filter-panel-head">
            <strong>按心情筛选</strong>
            <span>选择后立即更新列表</span>
          </div>
          <div class="mobile-filter-choices mood-choices">
            <button type="button" :class="{ selected: !moodKey }" @click="selectMood('')">
              全部心情
            </button>
            <button
              v-for="mood in moods"
              :key="mood.key"
              type="button"
              :class="{ selected: moodKey === mood.key }"
              @click="selectMood(mood.key)"
            >
              <span class="choice-emoji">{{ mood.emoji }}</span>
              <span>{{ mood.label }}</span>
            </button>
          </div>
        </template>
      </section>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ArrowDown, Download, RefreshLeft, Search } from '@element-plus/icons-vue'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'

const props = defineProps({
  tags: { type: Array, default: () => [] },
  moods: { type: Array, default: () => [] },
  exporting: { type: Boolean, default: false }
})
const emit = defineEmits(['filter', 'keyword-input', 'reset', 'export'])
const keyword = defineModel('keyword', { type: String, default: '' })
const startDate = defineModel('startDate', { type: String, default: '' })
const endDate = defineModel('endDate', { type: String, default: '' })
const tagId = defineModel('tagId', { default: null })
const moodKey = defineModel('moodKey', { type: String, default: '' })
const activePanel = ref('')

const hasDateFilter = computed(() => !!startDate.value || !!endDate.value)
const selectedTag = computed(() => props.tags.find(tag => tag.tagId === tagId.value) || null)
const selectedMood = computed(() => props.moods.find(mood => mood.key === moodKey.value) || null)
const dateFilterLabel = computed(() => hasDateFilter.value ? '日期 · 已选' : '日期')
const tagFilterLabel = computed(() => selectedTag.value?.name || '标签')
const moodFilterLabel = computed(() => selectedMood.value
  ? `${selectedMood.value.emoji} ${selectedMood.value.label}`
  : '心情')
const hasActiveFilters = computed(() => hasDateFilter.value
  || !!keyword.value.trim()
  || !!tagId.value
  || !!moodKey.value)

const togglePanel = (panel) => {
  activePanel.value = activePanel.value === panel ? '' : panel
}

const applyDateFilter = () => {
  if (startDate.value && endDate.value && startDate.value > endDate.value) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  activePanel.value = ''
  emit('filter')
}

const clearDateFilter = () => {
  startDate.value = ''
  endDate.value = ''
  activePanel.value = ''
  emit('filter')
}

const selectTag = (value) => {
  tagId.value = value
  activePanel.value = ''
  emit('filter')
}

const selectMood = (value) => {
  moodKey.value = value
  activePanel.value = ''
  emit('filter')
}

const resetFilters = () => {
  activePanel.value = ''
  emit('reset')
}
</script>

<style src="./styles/DiaryMobileFilters.scss" scoped lang="scss"></style>
