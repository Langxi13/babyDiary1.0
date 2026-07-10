import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./Timeline.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/Timeline.scss', import.meta.url), 'utf8')
].join('\n')

test('timeline renders year month and week collapsible groups', () => {
  assert.match(source, /buildTimelineTree/)
  assert.match(source, /initialExpandedTimelineKeys/)
  assert.match(source, /timelineTree/)
  assert.match(source, /class="timeline-year-toggle"/)
  assert.match(source, /class="timeline-month-toggle"/)
  assert.match(source, /class="timeline-week-toggle"/)
  assert.match(source, /isExpanded\(year\.key\)/)
  assert.match(source, /isExpanded\(month\.key\)/)
  assert.match(source, /isExpanded\(week\.key\)/)
})

test('timeline defaults to expanding every visible group after data loads', () => {
  assert.match(source, /expandedKeys\.value = new Set\(initialExpandedTimelineKeys\(tree\)\)/)
  assert.doesNotMatch(source, /hasVisibleExpandedKey/)
})

test('timeline collapse layout keeps dense groups readable on mobile and desktop', () => {
  assert.match(source, /\.timeline-year-toggle\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(source, /\.timeline-summary\s*\{[\s\S]*?flex-wrap:\s*wrap;/)
  assert.match(source, /\.timeline-counts\s*\{[\s\S]*?white-space:\s*nowrap;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.timeline-year-toggle/)
})

test('mobile timeline uses compact grouped list cards with in-card dates', () => {
  assert.match(source, /class="mobile-date-pill"/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.timeline-year-body\s*\{[\s\S]*?padding-left:\s*0;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.month-items\s*\{[\s\S]*?padding-left:\s*0;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.week-items\s*\{[\s\S]*?padding-left:\s*0;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.date-chip\s*\{[\s\S]*?display:\s*none;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.mobile-date-pill\s*\{[\s\S]*?display:\s*inline-flex;/)
})

test('timeline hierarchy gives year month and week distinct visual levels', () => {
  assert.match(source, /\.timeline-year-toggle\s*\{[\s\S]*?border-left:\s*5px solid #b14f64;/)
  assert.match(source, /\.timeline-year-toggle\s*\{[\s\S]*?background:\s*linear-gradient/)
  assert.match(source, /\.timeline-month-toggle\s*\{[\s\S]*?border-left:\s*4px solid #d89068;/)
  assert.match(source, /\.timeline-week-toggle\s*\{[\s\S]*?border-style:\s*dashed;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.timeline-year-toggle\s*\{[\s\S]*?border-left-width:\s*5px;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.timeline-month-toggle\s*\{[\s\S]*?border-left-width:\s*4px;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.timeline-week-toggle\s*\{[\s\S]*?border-left:\s*3px solid #7ca294;/)
})
