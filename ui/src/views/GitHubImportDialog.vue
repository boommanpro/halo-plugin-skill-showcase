<script setup lang="ts">
import { ref, computed } from "vue";
import axios from "axios";
import { VButton, Toast } from "@halo-dev/components";

const props = defineProps<{
  categories: any[];
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "imported"): void;
}>();

const apiBase = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

const githubUrl = ref("");
const parsing = ref(false);
const importing = ref(false);
const preview = ref<any>(null);
const parseError = ref("");

const form = ref({
  displayName: "",
  slug: "",
  category: "",
  summary: "",
  tags: "",
  published: true,
});

const step = computed(() => (preview.value ? 2 : 1));

function formatSize(bytes: number | null | undefined) {
  if (!bytes || bytes <= 0) return "0 B";
  if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + " MB";
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + " KB";
  return bytes + " B";
}

async function parseUrl() {
  if (!githubUrl.value.trim()) {
    Toast.warning("请输入 GitHub 仓库地址");
    return;
  }
  parsing.value = true;
  parseError.value = "";
  preview.value = null;
  try {
    const res = await axios.post(`${apiBase}/skills/ghimport/preview`, {
      url: githubUrl.value.trim(),
    });
    preview.value = res.data;
    form.value.displayName = res.data.displayName || "";
    form.value.slug = res.data.suggestedSlug || "";
    form.value.summary = res.data.summary || "";
  } catch (e: any) {
    const msg =
      typeof e.response?.data === "string"
        ? e.response.data
        : e.response?.data?.message || e.message || "解析失败";
    parseError.value = msg;
    Toast.error(msg);
  } finally {
    parsing.value = false;
  }
}

async function doImport() {
  if (!form.value.displayName.trim()) {
    Toast.warning("请输入 Skill 名称");
    return;
  }
  if (!form.value.category) {
    Toast.warning("请选择分类");
    return;
  }
  importing.value = true;
  try {
    await axios.post(`${apiBase}/skills/ghimport`, {
      url: githubUrl.value.trim(),
      slug: form.value.slug || undefined,
      displayName: form.value.displayName,
      category: form.value.category,
      summary: form.value.summary || undefined,
      tags: form.value.tags
        ? form.value.tags.split(",").map((t) => t.trim()).filter(Boolean)
        : [],
      published: form.value.published,
    });
    Toast.success("导入成功");
    emit("imported");
  } catch (e: any) {
    const msg =
      typeof e.response?.data === "string"
        ? e.response.data
        : e.response?.data?.message || e.message || "导入失败";
    Toast.error(msg);
  } finally {
    importing.value = false;
  }
}

function resetToStep1() {
  preview.value = null;
  parseError.value = "";
}
</script>

<template>
  <div class="dialog-mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <h3>GitHub 导入</h3>
        <button class="close-btn" @click="emit('close')">×</button>
      </div>

      <div class="dialog-body">
        <!-- Step 1: URL 解析 -->
        <div v-if="step === 1">
          <label class="field-label">仓库地址</label>
          <div class="url-row">
            <input
              v-model="githubUrl"
              type="text"
              placeholder="https://github.com/owner/repo 或 owner/repo（子目录用 /tree/branch/subdir）"
              @keyup.enter="parseUrl"
            />
            <VButton type="primary" :loading="parsing" @click="parseUrl">
              解析
            </VButton>
          </div>
          <p v-if="parseError" class="error-text">{{ parseError }}</p>
          <div class="tips">
            <p>· 仓库需包含 SKILL.md / README.md（技能包）或 index.html（演示站）</p>
            <p>· 技能在子目录时，粘贴 github.com/owner/repo/tree/main/skills/xxx 形式的地址</p>
            <p>· 导入后可随时「检查更新 + 同步」上游新版本</p>
          </div>
        </div>

        <!-- Step 2: 预览 + 表单 -->
        <template v-else>
          <div class="preview-card">
            <div class="preview-row">
              <span class="preview-label">仓库</span>
              <a
                :href="`https://github.com/${preview.repoSlug}`"
                target="_blank"
                rel="noopener"
                class="preview-link"
              >{{ preview.repoSlug }}</a>
              <span v-if="preview.stars" class="preview-star">★ {{ preview.stars }}</span>
            </div>
            <div class="preview-row">
              <span class="preview-label">分支</span>
              <span>{{ preview.ref }}</span>
              <span class="preview-sha">@ {{ (preview.resolvedSha || "").slice(0, 7) }}</span>
            </div>
            <div class="preview-row">
              <span class="preview-label">解析</span>
              <span>
                {{ preview.fileCount }} 个文件 · {{ formatSize(preview.extractedSize) }}
                <template v-if="preview.entryFile"> · 入口 {{ preview.entryFile }}</template>
                <template v-else> · 网站演示包</template>
              </span>
            </div>
            <div class="preview-row">
              <span class="preview-label">上游版本</span>
              <span>v{{ preview.version }}</span>
            </div>
          </div>

          <label class="field-label">名称</label>
          <input v-model="form.displayName" type="text" placeholder="Skill 名称" />

          <div class="form-grid">
            <div>
              <label class="field-label">Slug（URL 路径）</label>
              <input v-model="form.slug" type="text" placeholder="my-skill" />
            </div>
            <div>
              <label class="field-label">分类 *</label>
              <select v-model="form.category">
                <option value="" disabled>请选择分类</option>
                <option v-for="cat in categories" :key="cat.name" :value="cat.name">
                  {{ cat.displayName || cat.name }}
                </option>
              </select>
            </div>
          </div>

          <label class="field-label">简介</label>
          <textarea v-model="form.summary" rows="3" placeholder="Skill 简介（默认取 SKILL.md description）"></textarea>

          <label class="field-label">标签（逗号分隔）</label>
          <input v-model="form.tags" type="text" placeholder="claude,skill,工具" />

          <label class="checkbox-row">
            <input v-model="form.published" type="checkbox" />
            <span>立即发布</span>
          </label>
        </template>
      </div>

      <div class="dialog-footer">
        <VButton v-if="step === 2" @click="resetToStep1">上一步</VButton>
        <VButton @click="emit('close')">取消</VButton>
        <VButton
          v-if="step === 2"
          type="primary"
          :loading="importing"
          @click="doImport"
        >
          导入
        </VButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: var(--color-bg, #fff);
  border-radius: 8px;
  width: 560px;
  max-width: 92vw;
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.18);
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-divider, #e5e7eb);
}
.dialog-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--color-text, #111827);
}
.close-btn {
  background: none;
  border: 0;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text-secondary, #6b7280);
  line-height: 1;
}
.dialog-body {
  padding: 18px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-divider, #e5e7eb);
}
.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary, #4b5563);
  margin: 6px 0 4px;
  display: block;
}
.url-row {
  display: flex;
  gap: 10px;
}
.url-row input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--color-border, #d1d5db);
  border-radius: 6px;
  font-size: 14px;
  background: var(--color-bg, #fff);
  color: var(--color-text, #111827);
}
.dialog-body input[type="text"],
.dialog-body select,
.dialog-body textarea {
  padding: 8px 12px;
  border: 1px solid var(--color-border, #d1d5db);
  border-radius: 6px;
  font-size: 14px;
  background: var(--color-bg, #fff);
  color: var(--color-text, #111827);
  width: 100%;
  box-sizing: border-box;
}
.error-text {
  color: #ef4444;
  font-size: 13px;
  margin: 4px 0;
}
.tips {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--color-gray-100, #f9fafb);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-text-secondary, #6b7280);
  line-height: 1.8;
}
.tips p {
  margin: 0;
}
.preview-card {
  border: 1px solid var(--color-divider, #e5e7eb);
  border-radius: 6px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 6px;
  background: var(--color-gray-100, #f9fafb);
}
.preview-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text, #111827);
  flex-wrap: wrap;
}
.preview-label {
  color: var(--color-text-secondary, #6b7280);
  min-width: 62px;
  font-size: 12px;
}
.preview-link {
  color: var(--color-primary, #00b8a6);
  text-decoration: none;
  font-weight: 500;
}
.preview-sha {
  font-family: monospace;
  font-size: 12px;
  color: var(--color-text-secondary, #6b7280);
}
.preview-star {
  color: #f59e0b;
  font-size: 12px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 14px;
  color: var(--color-text, #111827);
  cursor: pointer;
}
</style>
