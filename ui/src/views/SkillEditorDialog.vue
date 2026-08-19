<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";
import axios from "axios";
import { VButton, Toast } from "@halo-dev/components";

const props = defineProps<{
  skill: any;
  categories: any[];
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "saved"): void;
}>();

const apiBase = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

const isEdit = computed(() => !!props.skill?.name);
const saving = ref(false);

const form = ref({
  displayName: "",
  category: "",
  summary: "",
  content: "",
  tags: [] as string[],
  tagInput: "",
  sourceType: "markdown" as "markdown" | "zip" | "external",
  externalUrl: "",
  sortOrder: 0,
  published: true,
});

watch(
  () => props.skill,
  (s) => {
    if (s) {
      form.value.displayName = s.displayName || "";
      form.value.category = s.category || "";
      form.value.summary = s.summary || "";
      form.value.content = s.content || "";
      form.value.tags = s.tags || [];
      form.value.sourceType = s.sourceType || "markdown";
      form.value.externalUrl = s.externalUrl || "";
      form.value.sortOrder = s.sortOrder ?? 0;
      form.value.published = s.published ?? true;
    } else {
      form.value = {
        displayName: "",
        category: props.categories[0]?.name || "",
        summary: "",
        content: "",
        tags: [],
        tagInput: "",
        sourceType: "markdown",
        externalUrl: "",
        sortOrder: 0,
        published: true,
      };
    }
    // 标签编辑态重置
    editingTagIndex.value = -1;
    editingTagValue.value = "";
  },
  { immediate: true }
);

function addTag() {
  const tag = form.value.tagInput.trim();
  if (tag && !form.value.tags.includes(tag)) {
    form.value.tags.push(tag);
  }
  form.value.tagInput = "";
}

function removeTag(tag: string) {
  const idx = form.value.tags.indexOf(tag);
  if (idx >= 0) form.value.tags.splice(idx, 1);
  if (editingTagIndex.value === idx) editingTagIndex.value = -1;
}

// 标签内联编辑:点击标签即可修改文本
const editingTagIndex = ref(-1);
const editingTagValue = ref("");
const editInputRef = ref<HTMLInputElement | null>(null);

function setEditInputRef(el: any) {
  editInputRef.value = el as HTMLInputElement | null;
}

async function startEditTag(index: number) {
  editingTagIndex.value = index;
  editingTagValue.value = form.value.tags[index] || "";
  await nextTick();
  editInputRef.value?.focus();
  editInputRef.value?.select();
}

function commitEditTag() {
  const idx = editingTagIndex.value;
  if (idx < 0) return;
  const newTag = editingTagValue.value.trim();
  if (newTag && !form.value.tags.includes(newTag)) {
    form.value.tags[idx] = newTag;
  } else if (!newTag) {
    form.value.tags.splice(idx, 1);
  } else {
    // 与已有标签重复:保留原值
    form.value.tags[idx] = form.value.tags[idx];
  }
  editingTagIndex.value = -1;
  editingTagValue.value = "";
}

function cancelEditTag() {
  editingTagIndex.value = -1;
  editingTagValue.value = "";
}

function generateSlug(text: string): string {
  const asciiSlug = text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .substring(0, 40);
  return asciiSlug || "skill";
}

async function save() {
  if (!form.value.displayName.trim()) {
    alert("请输入 Skill 名称");
    return;
  }
  if (!form.value.category) {
    alert("请选择分类");
    return;
  }
  saving.value = true;
  try {
    const slug = generateSlug(form.value.displayName);
    const timestamp = Date.now().toString(36);
    const payload: any = {
      apiVersion: "skill-showcase.halo.run/v1alpha1",
      kind: "Skill",
      metadata: isEdit.value
        ? { name: props.skill.name, version: props.skill?.version || 1 }
        : { generateName: `${slug}-${timestamp}-` },
      spec: {
        displayName: form.value.displayName,
        category: form.value.category,
        summary: form.value.summary,
        content: form.value.sourceType === "markdown" ? form.value.content : "",
        tags: form.value.tags,
        sourceType: form.value.sourceType,
        externalUrl: form.value.sourceType === "external" ? form.value.externalUrl : "",
        sortOrder: form.value.sortOrder,
        published: form.value.published,
      },
    };

    if (isEdit.value) {
      await axios.put(`${apiBase}/skills/${props.skill.name}`, payload);
    } else {
      await axios.post(`${apiBase}/skills`, payload);
    }
    Toast.success("保存成功");
    emit("saved");
  } catch (e: any) {
    Toast.error("保存失败: " + (e.response?.data?.message || e.message));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="dialog-overlay" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <h3>{{ isEdit ? "编辑 Skill" : "新增 Skill" }}</h3>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>
      <div class="dialog-body">
        <div class="form-group">
          <label>显示名称 *</label>
          <input v-model="form.displayName" type="text" placeholder="例如：Java 后端开发" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>分类 *</label>
            <select v-model="form.category">
              <option value="">请选择分类</option>
              <option v-for="cat in categories" :key="cat.name" :value="cat.name">
                {{ cat.displayName }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>排序</label>
            <input v-model.number="form.sortOrder" type="number" />
          </div>
        </div>
        <div class="form-group">
          <label>简短描述</label>
          <textarea v-model="form.summary" rows="2" placeholder="在卡片上显示的简短介绍"></textarea>
        </div>
        <div class="form-group">
          <label>标签（点击标签可直接编辑）</label>
          <div class="tags-input">
            <template v-for="(tag, i) in form.tags" :key="i">
              <input
                v-if="editingTagIndex === i"
                :ref="setEditInputRef"
                v-model="editingTagValue"
                type="text"
                class="tag-edit-input"
                @keyup.enter.prevent="commitEditTag"
                @keyup.esc="cancelEditTag"
                @blur="commitEditTag"
              />
              <span v-else class="tag-chip" @click="startEditTag(i)">
                {{ tag }}
                <button @click.stop="removeTag(tag)">×</button>
              </span>
            </template>
            <input
              v-model="form.tagInput"
              type="text"
              placeholder="输入标签后按回车"
              @keyup.enter.prevent="addTag"
              v-if="!form.tags.length || form.tagInput"
            />
          </div>
        </div>
        <div class="form-group">
          <label>内容类型 *</label>
          <div class="radio-group">
            <label class="radio-item">
              <input type="radio" v-model="form.sourceType" value="markdown" />
              <span>Markdown（支持代码高亮）</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.sourceType" value="external" />
              <span>外链</span>
            </label>
          </div>
        </div>
        <div v-if="form.sourceType === 'markdown'" class="form-group">
          <label>Markdown 内容</label>
          <div class="editor-wrapper">
            <textarea
              v-model="form.content"
              rows="12"
              placeholder="# 标题&#10;&#10;描述内容...&#10;&#10;```java&#10;public class Hello { }&#10;```"
              class="code-editor"
            ></textarea>
          </div>
        </div>
        <div v-if="form.sourceType === 'external'" class="form-group">
          <label>外部链接 URL</label>
          <input v-model="form.externalUrl" type="text" placeholder="https://..." />
        </div>
        <div class="form-group checkbox">
          <label>
            <input type="checkbox" v-model="form.published" />
            <span>立即发布（前台可见）</span>
          </label>
        </div>
      </div>
      <div class="dialog-footer">
        <VButton :disabled="saving" @click="emit('close')">取消</VButton>
        <VButton type="primary" :disabled="saving" @click="save">
          {{ saving ? "保存中..." : "保存" }}
        </VButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: var(--color-bg, #fff);
  border-radius: 12px;
  width: 640px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-divider, #e5e7eb);
}
.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text, #111827);
}
.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--color-text-secondary, #6b7280);
}
.dialog-body {
  padding: 20px 24px;
}
.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text, #374151);
}
.form-group input[type="text"],
.form-group input[type="number"],
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border, #d1d5db);
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
  background: var(--color-bg, #fff);
  color: var(--color-text, #111827);
}
.form-group textarea {
  resize: vertical;
}
.form-group.checkbox label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-text, #111827);
}
.form-row {
  display: flex;
  gap: 12px;
}
.form-row .form-group {
  flex: 1;
}
.tags-input {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  border: 1px solid var(--color-border, #d1d5db);
  border-radius: 6px;
  min-height: 38px;
  align-items: center;
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-gray-100, #f3f4f6);
  color: var(--color-primary, #00b8a6);
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}
.tag-chip button {
  background: none;
  border: none;
  color: var(--color-primary, #00b8a6);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.tag-edit-input {
  border: 1px solid var(--color-primary, #00b8a6);
  outline: none;
  font-size: 13px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-bg, #fff);
  color: var(--color-text, #111827);
}
.tags-input input {
  border: none;
  outline: none;
  font-size: 14px;
  flex: 1;
  min-width: 100px;
  background: transparent;
  color: var(--color-text, #111827);
}
.radio-group {
  display: flex;
  gap: 16px;
}
.radio-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text, #111827);
}
.editor-wrapper {
  border: 1px solid var(--color-border, #d1d5db);
  border-radius: 6px;
  overflow: hidden;
}
.code-editor {
  width: 100%;
  padding: 12px;
  border: none;
  font-family: "Menlo", "Monaco", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  box-sizing: border-box;
  min-height: 240px;
  background: var(--color-bg, #fff);
  color: var(--color-text, #111827);
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-divider, #e5e7eb);
}
</style>