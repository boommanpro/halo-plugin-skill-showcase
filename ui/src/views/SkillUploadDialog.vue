<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";

const props = defineProps<{
  categories: any[];
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "uploaded"): void;
}>();

const apiBase = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const uploading = ref(false);
const uploadProgress = ref(0);
const form = ref({
  displayName: "",
  slug: "",
  category: "",
  summary: "",
  coverImage: "",
  tags: "" as string,
});

function onFileSelect(e: Event) {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files[0]) {
    selectedFile.value = target.files[0];
    if (!form.value.displayName) {
      form.value.displayName = selectedFile.value.name.replace(/\.zip$/i, "");
    }
  }
}

function onDragOver(e: DragEvent) {
  e.preventDefault();
}

function onDrop(e: DragEvent) {
  e.preventDefault();
  if (e.dataTransfer?.files[0]) {
    selectedFile.value = e.dataTransfer.files[0];
    if (!form.value.displayName) {
      form.value.displayName = selectedFile.value.name.replace(/\.zip$/i, "");
    }
  }
}

async function upload() {
  if (!selectedFile.value) {
    alert("请选择 ZIP 文件");
    return;
  }
  if (!form.value.displayName.trim()) {
    alert("请输入 Skill 名称");
    return;
  }
  if (!form.value.category) {
    alert("请选择分类");
    return;
  }

  uploading.value = true;
  uploadProgress.value = 0;

  const formData = new FormData();
  formData.append("file", selectedFile.value);
  formData.append("displayName", form.value.displayName);
  if (form.value.slug) formData.append("slug", form.value.slug);
  formData.append("category", form.value.category);
  if (form.value.summary) formData.append("summary", form.value.summary);
  if (form.value.coverImage) formData.append("coverImage", form.value.coverImage);
  if (form.value.tags) formData.append("tags", form.value.tags);

  try {
    await axios.post(`${apiBase}/skills/upload`, formData, {
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress: (progress) => {
        if (progress.total) {
          uploadProgress.value = Math.round((progress.loaded / progress.total) * 100);
        }
      },
    });
    emit("uploaded");
  } catch (e: any) {
    alert("上传失败: " + (e.response?.data || e.message));
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <div class="dialog-overlay" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <h3>ZIP 上传 Skill</h3>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>
      <div class="dialog-body">
        <div
          class="drop-zone"
          :class="{ hasFile: selectedFile }"
          @dragover="onDragOver"
          @drop="onDrop"
          @click="fileInput?.click()"
        >
          <input
            ref="fileInput"
            type="file"
            accept=".zip"
            style="display: none"
            @change="onFileSelect"
          />
          <template v-if="selectedFile">
            <div class="file-info">
              <div class="file-icon">📦</div>
              <div class="file-name">{{ selectedFile.name }}</div>
              <div class="file-size">{{ (selectedFile.size / 1024).toFixed(1) }} KB</div>
            </div>
          </template>
          <template v-else>
            <div class="drop-hint">
              <div class="drop-icon">📁</div>
              <div>点击选择或拖拽 ZIP 文件到此处</div>
              <div class="drop-sub">ZIP 内必须包含 index.html</div>
            </div>
          </template>
        </div>

        <div class="form-group">
          <label>Skill 名称 *</label>
          <input v-model="form.displayName" type="text" placeholder="显示名称" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>URL Slug</label>
            <input v-model="form.slug" type="text" placeholder="留空自动生成" />
          </div>
          <div class="form-group">
            <label>分类 *</label>
            <select v-model="form.category">
              <option value="">请选择分类</option>
              <option v-for="cat in categories" :key="cat.name" :value="cat.name">
                {{ cat.displayName }}
              </option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>简短描述</label>
          <textarea v-model="form.summary" rows="2" placeholder="在卡片上显示的简短介绍"></textarea>
        </div>
        <div class="form-group">
          <label>封面图 URL</label>
          <input v-model="form.coverImage" type="text" placeholder="https://..." />
        </div>
        <div class="form-group">
          <label>标签（逗号分隔）</label>
          <input v-model="form.tags" type="text" placeholder="tag1,tag2,tag3" />
        </div>

        <div v-if="uploading" class="progress">
          <div class="progress-bar" :style="{ width: uploadProgress + '%' }"></div>
          <span class="progress-text">{{ uploadProgress }}%</span>
        </div>
      </div>
      <div class="dialog-footer">
        <button class="btn btn-secondary" :disabled="uploading" @click="emit('close')">
          取消
        </button>
        <button
          class="btn btn-primary"
          :disabled="uploading || !selectedFile"
          @click="upload"
        >
          {{ uploading ? "上传中..." : "上传并创建" }}
        </button>
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
  width: 560px;
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
.drop-zone {
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 16px;
}
.drop-zone:hover,
.drop-zone.hasFile {
  border-color: #3b82f6;
  background: #eff6ff;
}
.drop-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.drop-hint {
  color: #6b7280;
  font-size: 14px;
}
.drop-sub {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 6px;
}
.file-icon {
  font-size: 48px;
  margin-bottom: 8px;
}
.file-name {
  font-weight: 500;
  color: #111827;
}
.file-size {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
}
.form-group {
  margin-bottom: 14px;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}
.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
}
.form-row {
  display: flex;
  gap: 12px;
}
.form-row .form-group {
  flex: 1;
}
.progress {
  position: relative;
  height: 24px;
  background: #f3f4f6;
  border-radius: 12px;
  overflow: hidden;
  margin-top: 12px;
}
.progress-bar {
  height: 100%;
  background: #3b82f6;
  transition: width 0.3s;
}
.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  color: #374151;
  font-weight: 500;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
}
.btn {
  padding: 8px 16px;
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
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>