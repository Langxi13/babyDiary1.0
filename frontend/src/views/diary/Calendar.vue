<template>
  <div class="page-shell">
    <main class="page-container">
      <div class="page-title-row">
        <div>
          <h1>日历</h1>
          <p>按日期找到写下的回忆</p>
        </div>
        <el-date-picker
          class="calendar-month-picker"
          v-model="monthValue"
          type="month"
          format="YYYY年MM月"
          value-format="YYYY-MM"
          @change="fetchCalendar"
        />
      </div>

      <el-card class="calendar-panel" shadow="never" v-loading="loading">
        <el-calendar v-model="selectedDate">
          <template #date-cell="{ data }">
            <button class="day-cell" :class="{ active: dayMap[data.day] }" @click="openDay(data.day)">
              <span class="day-number">{{ Number(data.day.slice(-2)) }}</span>
              <span v-if="dayMap[data.day]" class="day-note">{{ dayMap[data.day].count }} 篇</span>
              <span v-if="dayMap[data.day]" class="day-title" :title="dayMap[data.day].firstTitle">
                {{ compactDayTitle(dayMap[data.day].firstTitle) }}
              </span>
            </button>
          </template>
        </el-calendar>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElCalendar } from 'element-plus/es/components/calendar/index.mjs'
import { ElCard } from 'element-plus/es/components/card/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { diaryApi } from '@/api/diary'
import 'element-plus/es/components/calendar/style/css.mjs'
import 'element-plus/es/components/card/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'

const router = useRouter()
const loading = ref(false)
const selectedDate = ref(new Date())
const monthValue = ref(new Date().toISOString().slice(0, 7))
const days = ref([])
const fetchSeq = ref(0)
const MAX_DAY_TITLE_LENGTH = 8
let syncingFromPicker = false

const dayMap = computed(() => {
  return days.value.reduce((map, day) => {
    map[day.date] = day
    return map
  }, {})
})

const fetchCalendar = async () => {
  if (!monthValue.value) return
  const seq = fetchSeq.value + 1
  fetchSeq.value = seq
  loading.value = true
  try {
    const [year, month] = monthValue.value.split('-')
    syncingFromPicker = true
    selectedDate.value = new Date(Number(year), Number(month) - 1, 1)
    const response = await diaryApi.getCalendar({ year: Number(year), month: Number(month) })
    if (seq === fetchSeq.value) {
      days.value = response.data || []
    }
  } finally {
    if (seq === fetchSeq.value) {
      syncingFromPicker = false
      loading.value = false
    }
  }
}

const openDay = (day) => {
  const item = dayMap.value[day]
  if (!item) {
    return
  }
  if (item.count === 1 && item.firstDiaryId) {
    router.push(`/diaries/${item.firstDiaryId}`)
    return
  }
  router.push(`/diaries?date=${day}`)
}

const compactDayTitle = (title) => {
  const normalized = String(title || '').trim()
  return normalized.length > MAX_DAY_TITLE_LENGTH
    ? `${normalized.slice(0, MAX_DAY_TITLE_LENGTH)}...`
    : normalized
}

onMounted(fetchCalendar)

watch(selectedDate, (date) => {
  if (syncingFromPicker || !date) return
  const nextMonth = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  if (nextMonth !== monthValue.value) {
    monthValue.value = nextMonth
    fetchCalendar()
  }
})
</script>

<style scoped lang="scss">
.page-shell {
  min-height: 100vh;
  background: #f4f7f6;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;

  h1 {
    font-size: 26px;
    color: #263331;
  }

  p {
    margin-top: 6px;
    color: #65716e;
  }
}

.calendar-panel {
  border-radius: 8px;
  border-color: #dce8e4;
}

:deep(.el-calendar-day) {
  height: 94px;
  padding: 4px;
  overflow: hidden;
}

.day-cell {
  width: 100%;
  height: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;

  &.active {
    background: #edf8f5;
  }
}

.day-number {
  font-weight: 700;
  color: #2d3a37;
}

.day-note {
  display: block;
  margin-top: 6px;
  color: #2f9d8f;
  font-size: 12px;
}

.day-title {
  display: block;
  max-width: 100%;
  margin-top: 4px;
  color: #69736f;
  font-size: 12px;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .page-title-row {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .calendar-panel {
    border-radius: 8px;
    overflow: hidden;
  }

  :deep(.calendar-month-picker) {
    display: none;
  }

  :deep(.el-calendar__header) {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 12px 10px;
  }

  :deep(.el-card__body) {
    padding: 0;
  }

  :deep(.el-calendar__body) {
    padding: 8px;
  }

  :deep(.el-calendar-table thead th) {
    padding: 8px 0;
    color: #71807c;
    font-size: 12px;
  }

  :deep(.el-calendar-day) {
    height: 66px;
    padding: 2px;
  }

  :deep(.el-calendar__button-group) {
    width: 100%;
  }

  :deep(.el-calendar__button-group .el-button-group) {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;

    &::before,
    &::after {
      display: none;
    }

    .el-button {
      width: 100%;
      min-width: 0;
      margin-left: 0;
      padding: 0 8px;
    }
  }

  .day-cell {
    padding: 5px;
    border-radius: 6px;
  }

  .day-note {
    margin-top: 4px;
    font-size: 10px;
  }

  .day-title {
    display: none;
  }
}
</style>
