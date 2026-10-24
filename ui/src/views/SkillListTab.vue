<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import axios from "axios";
import { VButton, VTag, VSpace, Toast } from "@halo-dev/components";
import SkillEditorDialog from "./SkillEditorDialog.vue";
import SkillUploadDialog from "./SkillUploadDialog.vue";
import GitHubImportDialog from "./GitHubImportDialog.vue";
import CategoryManager from "./CategoryManager.vue";

const apiBase = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

const skills = ref<any[]>([]);
const categories = ref<any[]>([]);
const loading = ref(false);
const searchQuery = ref("");
const filterCategory = ref("");
const showEditor = ref(false);
const showUploader = ref(false);
const showCategoryMgr = ref(false);
const showGithubImport = ref(false);
const editingSkill = ref<any>(null);
// 更新模式下:记录被更新的 zip skill,交给上传对话框使用
const updatingSkill = ref<any>(null);
// github 来源技能的同步状态: { [name]: 'checking'|'syncing' }
const syncBusy = ref<Record<string, string>>({});
// 有上游新版本的 skill name 集合
const updatableSkills = ref<Set<string>>(new Set());

const filteredSkills = computed(() => {
  return skills.value.filter((s) => {
    if (filterCategory.value && s.category !== filterCategory.value) return false;
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase();
      return (
        s.displayName?.toLowerCase().includes(q) ||
        s.summary?.toLowerCase().includes(q) ||
        s.tags?.some((t: string) => t.toLowerCase().includes(q))
      );
    }
    return true;
  });
});

async function fetchSkills() {
  loading.value = true;
  try {
    const res = await axios.get(`${apiBase}/skills`);
    skills.value = res.data || [];
  } catch (e) {
    console.error("Failed to fetch skills", e);
  } finally {
    loading.value = false;
  }
}

async function fetchCategories() {
  try {
    const res = await axios.get(`${apiBase}/categories`);
    categories.value = res.data || [];
  } catch (e) {
    console.error("Failed to fetch categories", e);
  }
}

function openCreate() {
  editingSkill.value = null;
  updatingSkill.value = null;
  showEditor.value = false;
  showUploader.value = false;
  showEditor.value = true;
}

function openEdit(skill: any) {
  updatingSkill.value = null;
  showUploader.value = false;
  editingSkill.value = skill;
  showEditor.value = true;
}

function openUpdateZip(skill: any) {
  editingSkill.value = null;
  showEditor.value = false;
  updatingSkill.value = skill;
  showUploader.value = true;
}

async function handleDelete(name: string) {
  if (!confirm(`确定删除 Skill "${name}" 吗？`)) return;
  try {
    await axios.delete(`${apiBase}/skills/${name}`);
    Toast.success("已删除");
    await fetchSkills();
  } catch (e) {
    Toast.error("删除失败");
  }
}

async function togglePublished(skill: any) {
  try {
    const payload: any = {
      apiVersion: "skill-showcase.halo.run/v1alpha1",
      kind: "Skill",
      metadata: {
        name: skill.name,
        version: skill.version || 1,
      },
      spec: {
        ...skill,
        published: !skill.published,
      },
    };
    await axios.put(`${apiBase}/skills/${skill.name}`, payload);
    await fetchSkills();
  } catch (e) {
    Toast.error("更新失败");
  }
}

function getCategoryName(categoryId: string) {
  const cat = categories.value.find((c) => c.name === categoryId);
  return cat?.displayName || categoryId;
}

function sourceTypeMeta(type: string) {
  const map: Record<string, { text: string; color: string }> = {
    markdown: { text: "Markdown", color: "#22c55e" },
    zip: { text: "ZIP", color: "#0ea5e9" },
    github: { text: "GitHub", color: "#8b5cf6" },
    external: { text: "外链", color: "#f59e0b" },
  };
  return map[type] || { text: type, color: "#6b7280" };
}

function formatCount(n: number | null | undefined) {
  const num = Number(n || 0);
  if (num >= 10000) return (num / 10000).toFixed(1) + "w";
  if (num >= 1000) return (num / 1000).toFixed(1) + "k";
  return String(num);
}

async function checkUpstream(skill: any) {
  syncBusy.value = { ...syncBusy.value, [skill.name]: "checking" };
  try {
    const res = await axios.post(`${apiBase}/skills/${skill.name}/check-upstream`);
    const status = res.data;
    const next = new Set(updatableSkills.value);
    if (status.hasUpdate) {
      next.add(skill.name);
      Toast.info(status.message || "上游有新版本");
    } else {
      next.delete(skill.name);
      Toast.success(status.message || "已是最新");
    }
    updatableSkills.value = next;
  } catch (e: any) {
    Toast.error(
      typeof e.response?.data === "string" ? e.response.data : "检查更新失败"
    );
  } finally {
    const next = { ...syncBusy.value };
    delete next[skill.name];
    syncBusy.value = next;
  }
}

async function syncSkill(skill: any) {
  if (
    !confirm(
      `确定同步 "${skill.displayName}" 到上游最新版本吗？\n本地内容（文件/版本/简介）将被上游覆盖，分类和标签保留。`
    )
  ) {
    return;
  }
  syncBusy.value = { ...syncBusy.value, [skill.name]: "syncing" };
  try {
    await axios.post(`${apiBase}/skills/${skill.name}/sync`);
    const next = new Set(updatableSkills.value);
    next.delete(skill.name);
    updatableSkills.value = next;
    Toast.success("同步完成");
    await fetchSkills();
  } catch (e: any) {
    Toast.error(
      typeof e.response?.data === "string" ? e.response.data : "同步失败"
    );
  } finally {
    const next = { ...syncBusy.value };
    delete next[skill.name];
    syncBusy.value = next;
  }
}

function previewSkill(skill: any) {
  if (!skill.previewUrl) return;
  const url =
    /^https?:\/\//i.test(skill.previewUrl)
      ? skill.previewUrl
      : window.location.origin + skill.previewUrl;
  window.open(url, "_blank");
}

onMounted(async () => {
  await Promise.all([fetchSkills(), fetchCategories()]);
});
</script>

<template>
  <div class="skill-list-tab">
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="title">Skill 管理</h2>
      </div>
      <div class="toolbar-right">
        <select v-model="filterCategory" class="filter-select">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat.name" :value="cat.name">
            {{ cat.displayName }}
          </option>
        </select>
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索 Skill..."
          class="search-input"
        />
        <VButton @click="showCategoryMgr = true">分类管理</VButton>
        <VButton @click="openUpdateZip(null)">ZIP 上传</VButton>
        <VButton @click="showGithubImport = true">GitHub 导入</VButton>
        <VButton type="primary" @click="openCreate">+ 新增 Skill</VButton>
      </div>
    </div>

    <div class="table-container">
      <table class="skill-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>分类</th>
            <th>类型</th>
            <th>标签</th>
            <th style="width: 110px">浏览 / 下载</th>
            <th style="width: 80px">排序</th>
            <th style="width: 80px">发布</th>
            <th style="width: 340px; min-width: 340px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="loading-cell">加载中...</td>
          </tr>
          <tr v-else-if="filteredSkills.length === 0">
            <td colspan="8" class="empty-cell">
              暂无 Skill，点击"GitHub 导入"、"ZIP 上传"或"新增 Skill"添加
            </td>
          </tr>
          <tr v-for="skill in filteredSkills" :key="skill.name">
            <td>
              <div class="skill-name">
                {{ skill.displayName }}
                <span
                  v-if="updatableSkills.has(skill.name)"
                  class="update-dot"
                  title="上游有新版本"
                >●</span>
              </div>
              <div class="skill-summary">
                <template v-if="skill.sourceRepo">@{{ skill.sourceRepo }}</template>
                <template v-else>{{ skill.summary }}</template>
              </div>
            </td>
            <td>
              <VTag>{{ getCategoryName(skill.category) }}</VTag>
            </td>
            <td>
              <VTag :color="sourceTypeMeta(skill.sourceType).color">
                {{ sourceTypeMeta(skill.sourceType).text }}
              </VTag>
            </td>
            <td>
              <div class="tags-cell">
                <span v-for="tag in (skill.tags || []).slice(0, 3)" :key="tag" class="tag">
                  {{ tag }}
                </span>
                <span v-if="(skill.tags?.length || 0) > 3" class="tag more">
                  +{{ skill.tags.length - 3 }}
                </span>
              </div>
            </td>
            <td class="stats-cell">
              <span class="stat-inline" title="浏览">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                {{ formatCount(skill.views) }}
              </span>
              <span class="stat-inline" title="下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                {{ formatCount(skill.downloads) }}
              </span>
            </td>
            <td>{{ skill.sortOrder ?? 0 }}</td>
            <td>
              <label class="switch">
                <input
                  type="checkbox"
                  :checked="skill.published"
                  @change="togglePublished(skill)"
                />
                <span class="slider"></span>
              </label>
            </td>
            <td>
              <VSpace spacing="sm" :wrap="true">
                <VButton size="sm" @click="openEdit(skill)">编辑</VButton>
                <VButton size="sm" @click="previewSkill(skill)">预览</VButton>
                <VButton
                  v-if="skill.sourceType === 'zip'"
                  size="sm"
                  @click="openUpdateZip(skill)"
                >
                  更新
                </VButton>
                <template v-if="skill.sourceType === 'github'">
                  <VButton
                    size="sm"
                    :disabled="syncBusy[skill.name] !== undefined"
                    @click="checkUpstream(skill)"
                  >
                    {{ syncBusy[skill.name] === 'checking' ? '检查中…' : '检查更新' }}
                  </VButton>
                  <VButton
                    size="sm"
                    type="primary"
                    :disabled="syncBusy[skill.name] !== undefined"
                    @click="syncSkill(skill)"
                  >
                    {{ syncBusy[skill.name] === 'syncing' ? '同步中…' : '同步' }}
                  </VButton>
                </template>
                <VButton size="sm" type="danger" @click="handleDelete(skill.name)">
                  删除
                </VButton>
              </VSpace>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <SkillEditorDialog
      v-if="showEditor"
      :skill="editingSkill"
      :categories="categories"
      @close="showEditor = false"
      @saved="async () => { await fetchSkills(); await fetchCategories(); showEditor = false; }"
    />

    <SkillUploadDialog
      v-if="showUploader"
      :skill="updatingSkill"
      :categories="categories"
      @close="showUploader = false"
      @uploaded="async () => { await fetchSkills(); await fetchCategories(); showUploader = false; }"
    />

    <GitHubImportDialog
      v-if="showGithubImport"
      :categories="categories"
      @close="showGithubImport = false"
      @imported="async () => { await fetchSkills(); await fetchCategories(); showGithubImport = false; }"
    />

    <CategoryManager
      v-if="showCategoryMgr"
      :categories="categories"
      @close="showCategoryMgr = false"
      @changed="fetchCategories"
    />
  </div>
</template>

<style scoped>
.skill-list-tab {
  padding: 20px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-left .title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text, #111827);
}
.toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.filter-select,
.search-input {
  padding: 6px 12px;
  border: 1px solid var(--color-border, #d1d5db);
  border-radius: 6px;
  font-size: 14px;
  background: var(--color-bg, #fff);
  color: var(--color-text, #111827);
}
.search-input {
  width: 200px;
}
.table-container {
  background: var(--color-bg, #fff);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}
.skill-table {
  width: 100%;
  border-collapse: collapse;
}
.skill-table th {
  text-align: left;
  padding: 12px;
  background: var(--color-gray-100, #f9fafb);
  border-bottom: 1px solid var(--color-divider, #e5e7eb);
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-secondary, #6b7280);
}
.skill-table td {
  padding: 12px;
  border-bottom: 1px solid var(--color-divider, #f3f4f6);
  font-size: 14px;
  color: var(--color-text, #111827);
}
.skill-table tr:hover {
  background: var(--color-gray-100, #f9fafb);
}
.loading-cell,
.empty-cell {
  text-align: center;
  padding: 40px 20px !important;
  color: var(--color-text-secondary, #9ca3af);
}
.skill-name {
  font-weight: 500;
  color: var(--color-text, #111827);
  display: flex;
  align-items: center;
  gap: 6px;
}
.update-dot {
  color: #f59e0b;
  font-size: 10px;
}
.stats-cell {
  font-size: 12.5px;
  color: var(--color-text-secondary, #6b7280);
  white-space: nowrap;
}
.stat-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 10px;
}
.stat-inline svg {
  width: 13px;
  height: 13px;
  opacity: 0.7;
}
.skill-summary {
  font-size: 12px;
  color: var(--color-text-secondary, #6b7280);
  margin-top: 2px;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tags-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.tag {
  padding: 2px 6px;
  background: var(--color-gray-100, #f3f4f6);
  color: var(--color-text, #374151);
  border-radius: 3px;
  font-size: 11px;
}
.tag.more {
  background: transparent;
  color: var(--color-text-secondary, #6b7280);
}
.switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
}
.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--color-border, #d1d5db);
  transition: 0.3s;
  border-radius: 22px;
}
.slider:before {
  position: absolute;
  content: "";
  height: 16px;
  width: 16px;
  left: 3px;
  bottom: 3px;
  background-color: #fff;
  transition: 0.3s;
  border-radius: 50%;
}
input:checked + .slider {
  background-color: var(--color-primary, #00b8a6);
}
input:checked + .slider:before {
  transform: translateX(18px);
}
</style>