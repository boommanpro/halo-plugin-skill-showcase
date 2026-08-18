<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import axios from "axios";
import SkillEditorDialog from "./SkillEditorDialog.vue";
import SkillUploadDialog from "./SkillUploadDialog.vue";
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
const editingSkill = ref<any>(null);

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
  showEditor.value = true;
}

function openEdit(skill: any) {
  editingSkill.value = skill;
  showEditor.value = true;
}

async function handleDelete(name: string) {
  if (!confirm(`确定删除 Skill "${name}" 吗？`)) return;
  try {
    await axios.delete(`${apiBase}/skills/${name}`);
    await fetchSkills();
  } catch (e) {
    alert("删除失败");
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
    alert("更新失败");
  }
}

function getCategoryName(categoryId: string) {
  const cat = categories.value.find((c) => c.name === categoryId);
  return cat?.displayName || categoryId;
}

function sourceTypeBadge(type: string) {
  const map: Record<string, { text: string; color: string }> = {
    markdown: { text: "Markdown", color: "#22c55e" },
    zip: { text: "ZIP", color: "#3b82f6" },
    external: { text: "外链", color: "#f59e0b" },
  };
  return map[type] || { text: type, color: "#6b7280" };
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
        <button class="btn btn-secondary" @click="showCategoryMgr = true">
          分类管理
        </button>
        <button class="btn btn-secondary" @click="showUploader = true">
          ZIP 上传
        </button>
        <button class="btn btn-primary" @click="openCreate">
          + 新增 Skill
        </button>
      </div>
    </div>

    <div class="table-container">
      <table class="skill-table">
        <thead>
          <tr>
            <th style="width: 80px">封面</th>
            <th>名称</th>
            <th>分类</th>
            <th>类型</th>
            <th>标签</th>
            <th style="width: 100px">排序</th>
            <th style="width: 80px">发布</th>
            <th style="width: 180px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="loading-cell">加载中...</td>
          </tr>
          <tr v-else-if="filteredSkills.length === 0">
            <td colspan="8" class="empty-cell">暂无 Skill，点击"新增 Skill"或"ZIP 上传"添加</td>
          </tr>
          <tr v-for="skill in filteredSkills" :key="skill.name">
            <td>
              <div v-if="skill.coverImage" class="cover-thumb">
                <img :src="skill.coverImage" :alt="skill.displayName" />
              </div>
              <div v-else class="cover-thumb placeholder">
                {{ skill.displayName?.charAt(0) || "?" }}
              </div>
            </td>
            <td>
              <div class="skill-name">{{ skill.displayName }}</div>
              <div class="skill-summary">{{ skill.summary }}</div>
            </td>
            <td>
              <span class="category-tag">{{ getCategoryName(skill.category) }}</span>
            </td>
            <td>
              <span
                class="source-badge"
                :style="{ backgroundColor: sourceTypeBadge(skill.sourceType).color }"
              >
                {{ sourceTypeBadge(skill.sourceType).text }}
              </span>
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
              <div class="actions">
                <button class="btn-link" @click="openEdit(skill)">编辑</button>
                <button
                  v-if="skill.sourceType === 'zip'"
                  class="btn-link"
                  @click="window.open(skill.previewUrl, '_blank')"
                >
                  预览
                </button>
                <button class="btn-link danger" @click="handleDelete(skill.name)">
                  删除
                </button>
              </div>
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
      :categories="categories"
      @close="showUploader = false"
      @uploaded="async () => { await fetchSkills(); await fetchCategories(); showUploader = false; }"
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
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  background: white;
}
.search-input {
  width: 200px;
}
.btn {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-primary {
  background: #3b82f6;
  color: white;
}
.btn-primary:hover {
  background: #2563eb;
}
.btn-secondary {
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #d1d5db;
}
.btn-secondary:hover {
  background: #e5e7eb;
}
.table-container {
  background: white;
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
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  font-weight: 600;
  font-size: 13px;
  color: #6b7280;
}
.skill-table td {
  padding: 12px;
  border-bottom: 1px solid #f3f4f6;
  font-size: 14px;
}
.skill-table tr:hover {
  background: #f9fafb;
}
.loading-cell,
.empty-cell {
  text-align: center;
  padding: 40px 20px !important;
  color: #9ca3af;
}
.cover-thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
}
.cover-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-thumb.placeholder {
  font-size: 20px;
  font-weight: 600;
  color: #6b7280;
  background: #e5e7eb;
}
.skill-name {
  font-weight: 500;
  color: #111827;
}
.skill-summary {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-tag {
  padding: 2px 8px;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 4px;
  font-size: 12px;
}
.source-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: white;
}
.tags-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.tag {
  padding: 2px 6px;
  background: #f3f4f6;
  color: #374151;
  border-radius: 3px;
  font-size: 11px;
}
.tag.more {
  background: transparent;
  color: #6b7280;
}
.actions {
  display: flex;
  gap: 8px;
}
.btn-link {
  background: none;
  border: none;
  color: #3b82f6;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.btn-link:hover {
  text-decoration: underline;
}
.btn-link.danger {
  color: #ef4444;
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
  background-color: #d1d5db;
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
  background-color: white;
  transition: 0.3s;
  border-radius: 50%;
}
input:checked + .slider {
  background-color: #3b82f6;
}
input:checked + .slider:before {
  transform: translateX(18px);
}
</style>