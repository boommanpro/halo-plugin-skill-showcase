<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import axios from "axios";

const props = defineProps<{
  categories: any[];
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "changed"): void;
}>();

const apiBase = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

const localCats = ref<any[]>([]);
const saving = ref(false);

const showForm = ref(false);
const isEditing = ref(false);
const editingName = ref("");
const form = ref({
  displayName: "",
  description: "",
  icon: "📂",
  color: "#3b82f6",
  sortOrder: 0,
});

async function fetchCategories() {
  try {
    const res = await axios.get(`${apiBase}/categories`);
    localCats.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
}

function openCreate() {
  isEditing.value = false;
  form.value = { displayName: "", description: "", icon: "📂", color: "#3b82f6", sortOrder: 0 };
  showForm.value = true;
}

function openEdit(cat: any) {
  isEditing.value = true;
  editingName.value = cat.name;
  form.value = {
    displayName: cat.displayName || "",
    description: cat.description || "",
    icon: cat.icon || "📂",
    color: cat.color || "#3b82f6",
    sortOrder: cat.sortOrder ?? 0,
  };
  showForm.value = true;
}

function cancelForm() {
  showForm.value = false;
}

function generateSlug(text: string): string {
  const asciiSlug = text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .substring(0, 40);
  return asciiSlug || "category";
}

async function save() {
  if (!form.value.displayName.trim()) {
    alert("请输入分类名称");
    return;
  }
  saving.value = true;
  try {
    const slug = generateSlug(form.value.displayName);
    const timestamp = Date.now().toString(36);
    const payload: any = {
      apiVersion: "skill-showcase.halo.run/v1alpha1",
      kind: "SkillCategory",
      metadata: isEditing.value
        ? { name: editingName.value }
        : { generateName: `${slug}-${timestamp}-` },
      spec: form.value,
    };
    if (isEditing.value) {
      await axios.put(`${apiBase}/categories/${editingName.value}`, payload);
    } else {
      await axios.post(`${apiBase}/categories`, payload);
    }
    showForm.value = false;
    await fetchCategories();
    emit("changed");
  } catch (e: any) {
    alert("保存失败: " + (e.response?.data?.message || e.message));
  } finally {
    saving.value = false;
  }
}

async function deleteCat(name: string) {
  if (!confirm(`确定删除分类 "${name}" 吗？`)) return;
  try {
    await axios.delete(`${apiBase}/categories/${name}`);
    await fetchCategories();
    emit("changed");
  } catch (e: any) {
    alert("删除失败");
  }
}

onMounted(fetchCategories);
</script>

<template>
  <div class="dialog-overlay" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <h3>分类管理</h3>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>
      <div class="dialog-body">
        <div v-if="showForm" class="form-area">
          <div class="form-row">
            <div class="form-group">
              <label>分类名称 *</label>
              <input v-model="form.displayName" type="text" placeholder="显示名称" />
            </div>
            <div class="form-group">
              <label>描述</label>
              <input v-model="form.description" type="text" placeholder="可选" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>图标</label>
              <input v-model="form.icon" type="text" placeholder="emoji" class="icon-input" />
            </div>
            <div class="form-group">
              <label>颜色</label>
              <input v-model="form.color" type="color" class="color-input" />
            </div>
            <div class="form-group">
              <label>排序</label>
              <input v-model.number="form.sortOrder" type="number" placeholder="0" />
            </div>
          </div>
          <div class="form-actions">
            <button class="btn btn-primary" :disabled="saving" @click="save">
              {{ saving ? "保存中..." : "保存" }}
            </button>
            <button class="btn btn-secondary" @click="cancelForm">取消</button>
          </div>
        </div>
        <div v-else class="toolbar">
          <span class="hint">共 {{ localCats.length }} 个分类</span>
          <button class="btn btn-primary" @click="openCreate">+ 新增分类</button>
        </div>

        <div class="category-list" v-if="localCats.length > 0 && !showForm">
          <div
            v-for="cat in localCats"
            :key="cat.name"
            class="category-row"
            :style="{ borderLeftColor: cat.color || '#3b82f6' }"
          >
            <div class="cat-icon">{{ cat.icon || "📂" }}</div>
            <div class="cat-info">
              <div class="cat-name">{{ cat.displayName }}</div>
              <div class="cat-desc" v-if="cat.description">{{ cat.description }}</div>
            </div>
            <div class="cat-actions">
              <button class="btn-link" @click="openEdit(cat)">编辑</button>
              <button class="btn-link danger" @click="deleteCat(cat.name)">删除</button>
            </div>
          </div>
        </div>
        <div v-if="localCats.length === 0 && !showForm" class="empty">
          暂无分类，点击"新增分类"添加
        </div>
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
  background: white;
  border-radius: 12px;
  width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}
.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #6b7280;
}
.dialog-body {
  padding: 20px 24px;
}
.form-area {
  background: #f9fafb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.form-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.form-group {
  flex: 1;
}
.form-group label {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.form-group input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
}
.icon-input {
  width: 80px;
  text-align: center;
}
.color-input {
  width: 48px;
  height: 32px;
  padding: 2px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
}
.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.toolbar .hint {
  color: #6b7280;
  font-size: 14px;
}
.category-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.category-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  border-left: 4px solid;
  border-radius: 8px;
  background: #fafafa;
}
.cat-icon {
  font-size: 24px;
}
.cat-info {
  flex: 1;
}
.cat-name {
  font-weight: 500;
  color: #111827;
}
.cat-desc {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}
.cat-actions {
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
.empty {
  text-align: center;
  padding: 40px 20px;
  color: #9ca3af;
}
.btn {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}
.btn-primary {
  background: #3b82f6;
  color: white;
}
.btn-primary:hover {
  background: #2563eb;
}
.btn-primary:disabled {
  background: #93c5fd;
  cursor: not-allowed;
}
.btn-secondary {
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #d1d5db;
}
.btn-secondary:hover {
  background: #e5e7eb;
}
</style>