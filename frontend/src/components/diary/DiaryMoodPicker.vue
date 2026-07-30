<template>
  <div class="diary-mood-picker" role="radiogroup" aria-label="心情">
    <button
      v-for="item in moods"
      :key="item.key"
      type="button"
      :class="{ active: model === item.key }"
      role="radio"
      :aria-checked="model === item.key"
      :title="item.label"
      @click="select(item.key)"
    >
      <span aria-hidden="true">{{ item.emoji }}</span>
      <small>{{ item.label }}</small>
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  moods: { type: Array, default: () => [] },
  clearable: { type: Boolean, default: false }
})
const model = defineModel({ type: String, default: '' })

const select = key => {
  model.value = props.clearable && model.value === key ? '' : key
}
</script>

<style scoped>
.diary-mood-picker {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

button {
  min-width: 0;
  min-height: 66px;
  padding: 8px 4px;
  border: 1px solid #e3d9d4;
  border-radius: 8px;
  background: #fff;
  color: #756963;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  cursor: pointer;
}

button > span { font-size: 26px; line-height: 1; }
button > small { font-size: 12px; line-height: 1.2; }
button.active { border-color: #b8665f; background: #fff2ef; color: #783f3b; box-shadow: inset 0 0 0 1px #b8665f; }
button:focus-visible { outline: 2px solid #347f75; outline-offset: 2px; }

@media (max-width: 768px) {
  .diary-mood-picker { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  button { min-height: 60px; }
}
</style>
