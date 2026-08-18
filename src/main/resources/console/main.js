this["skill-showcase"] = function (Vue, axios) {
  "use strict";

  const { ref, reactive, computed, watch, onMounted, defineComponent, h } = Vue;

  const API_BASE = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

  // ==================== SVG Icons ====================
  const icons = {
    plus: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>',
    edit: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>',
    trash: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>',
    close: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
    upload: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>',
    search: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>',
    download: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>',
    star: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
    heart: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>',
    check: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',
    chevronUp: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="18 15 12 9 6 15"/></svg>',
    chevronDown: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>',
    package: '<svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>',
    dragHandle: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="5" r="1"/><circle cx="9" cy="12" r="1"/><circle cx="9" cy="19" r="1"/><circle cx="15" cy="5" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="15" cy="19" r="1"/></svg>',
    eye: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>',
    externalLink: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>',
  };

  function icon(name) {
    return icons[name] || "";
  }

  // ==================== Utility Functions ====================
  function generateName(displayName) {
    return displayName
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .substring(0, 40) || "skill";
  }

  function generateSlug(displayName) {
    return generateName(displayName) + "-" + Date.now().toString(36);
  }

  function getSkillTypeMeta(type) {
    return {
      markdown: { text: "Markdown", color: "#22c55e" },
      zip: { text: "ZIP", color: "#3b82f6" },
      external: { text: "外链", color: "#f59e0b" },
    }[type] || { text: type || "未知", color: "#6b7280" };
  }

  function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / 1024 / 1024).toFixed(1) + " MB";
  }

  // ==================== SkillEditorDialog ====================
  const SkillEditorDialog = defineComponent({
    name: "SkillEditorDialog",
    props: {
      skill: { type: Object, default: null },
      categories: { type: Array, default: () => [] },
    },
    emits: ["close", "saved"],
    setup(props, { emit }) {
      const isEdit = computed(() => !!(props.skill && props.skill.name));
      const saving = ref(false);

      const activeTab = ref("basic");

      const tabs = [
        { key: "basic", label: "基本信息", icon: "package" },
        { key: "media", label: "媒体文件", icon: "package" },
        { key: "content", label: "内容", icon: "package" },
        { key: "stats", label: "统计", icon: "package" },
      ];

      const form = reactive({
        displayName: "",
        category: "",
        version: "1.0.0",
        sortOrder: 0,
        published: true,
        coverImage: "",
        summary: "",
        author: "",
        authorAvatar: "",
        authorUrl: "",
        sourceType: "markdown",
        content: "",
        externalUrl: "",
        tags: [],
        tagInput: "",
        installCommand: "",
        downloads: 0,
        rating: 0,
        ratingCount: 0,
        favorites: 0,
      });

      watch(
        () => props.skill,
        (skill) => {
          if (skill) {
            form.displayName = skill.displayName || "";
            form.category = skill.category || "";
            form.version = skill.version || "1.0.0";
            form.sortOrder = skill.sortOrder ?? 0;
            form.published = skill.published ?? true;
            form.coverImage = skill.coverImage || "";
            form.summary = skill.summary || "";
            form.author = skill.author || "";
            form.authorAvatar = skill.authorAvatar || "";
            form.authorUrl = skill.authorUrl || "";
            form.sourceType = skill.sourceType || "markdown";
            form.content = skill.content || "";
            form.externalUrl = skill.externalUrl || "";
            form.tags = skill.tags || [];
            form.tagInput = "";
            form.installCommand = skill.installCommand || "";
            form.downloads = skill.downloads ?? 0;
            form.rating = skill.rating ?? 0;
            form.ratingCount = skill.ratingCount ?? 0;
            form.favorites = skill.favorites ?? 0;
          } else {
            const firstCat = props.categories[0];
            form.displayName = "";
            form.category = firstCat ? firstCat.name : "";
            form.version = "1.0.0";
            form.sortOrder = 0;
            form.published = true;
            form.coverImage = "";
            form.summary = "";
            form.author = "";
            form.authorAvatar = "";
            form.authorUrl = "";
            form.sourceType = "markdown";
            form.content = "";
            form.externalUrl = "";
            form.tags = [];
            form.tagInput = "";
            form.installCommand = "";
            form.downloads = 0;
            form.rating = 0;
            form.ratingCount = 0;
            form.favorites = 0;
          }
        },
        { immediate: true }
      );

      function addTag() {
        const tag = form.tagInput.trim();
        if (tag && !form.tags.includes(tag)) {
          form.tags.push(tag);
        }
        form.tagInput = "";
      }

      function removeTag(tag) {
        const idx = form.tags.indexOf(tag);
        if (idx >= 0) form.tags.splice(idx, 1);
      }

      async function handleSave() {
        if (!form.displayName.trim()) {
          alert("请输入 Skill 名称");
          activeTab.value = "basic";
          return;
        }
        if (!form.category) {
          alert("请选择分类");
          activeTab.value = "basic";
          return;
        }
        if (form.sourceType === "external" && !form.externalUrl.trim()) {
          alert("请输入外部链接 URL");
          activeTab.value = "content";
          return;
        }

        saving.value = true;
        try {
          const name = isEdit.value
            ? props.skill.name
            : generateSlug(form.displayName);

          const payload = {
            apiVersion: "skill-showcase.halo.run/v1alpha1",
            kind: "Skill",
            metadata: {
              name,
              ...(isEdit.value ? {} : { generateName: generateName(form.displayName) + "-" + Date.now().toString(36) + "-" }),
            },
            spec: {
              displayName: form.displayName,
              category: form.category,
              version: form.version,
              sortOrder: Number(form.sortOrder) || 0,
              published: form.published,
              coverImage: form.coverImage,
              summary: form.summary,
              author: form.author,
              authorAvatar: form.authorAvatar,
              authorUrl: form.authorUrl,
              sourceType: form.sourceType,
              content: form.sourceType === "markdown" ? form.content : "",
              externalUrl: form.sourceType === "external" ? form.externalUrl : "",
              tags: form.tags,
              installCommand: form.installCommand,
              downloads: Number(form.downloads) || 0,
              rating: Math.min(5, Math.max(0, Number(form.rating) || 0)),
              ratingCount: Number(form.ratingCount) || 0,
              favorites: Number(form.favorites) || 0,
            },
          };

          if (isEdit.value) {
            await axios.put(`${API_BASE}/skills/${name}`, payload);
          } else {
            await axios.post(`${API_BASE}/skills`, payload);
          }
          emit("saved");
        } catch (err) {
          const msg = err?.response?.data?.message || err.message || "未知错误";
          alert("保存失败: " + msg);
        } finally {
          saving.value = false;
        }
      }

      return () =>
        h(
          "div",
          {
            class: "dialog-overlay",
            onClick: (e) => {
              if (e.target === e.currentTarget) emit("close");
            },
          },
          [
            h(
              "div",
              { class: "dialog", style: "max-width: 720px;" },
              [
                h("div", { class: "dialog-header" }, [
                  h("h3", isEdit.value ? "编辑 Skill" : "新增 Skill"),
                  h(
                    "button",
                    {
                      class: "close-btn",
                      onClick: () => emit("close"),
                      innerHTML: icon("close"),
                    }
                  ),
                ]),
                h("div", { class: "dialog-body" }, [
                  // Tab navigation
                  h(
                    "div",
                    { class: "tab-nav" },
                    tabs.map((t) =>
                      h(
                        "button",
                        {
                          class: [
                            "tab-btn",
                            { active: activeTab.value === t.key },
                          ],
                          onClick: () => (activeTab.value = t.key),
                        },
                        t.label
                      )
                    )
                  ),
                  // Tab content
                  h(
                    "div",
                    { class: "tab-content" },
                    renderTabContent(activeTab.value, form, props.categories, addTag, removeTag)
                  ),
                ]),
                h("div", { class: "dialog-footer" }, [
                  h(
                    "button",
                    {
                      class: "btn btn-secondary",
                      onClick: () => emit("close"),
                    },
                    "取消"
                  ),
                  h(
                    "button",
                    {
                      class: "btn btn-primary",
                      disabled: saving.value,
                      onClick: handleSave,
                    },
                    saving.value ? "保存中..." : "保存"
                  ),
                ]),
              ]
            ),
          ]
        );
    },
  });

  function renderTabContent(activeTab, form, categories, addTag, removeTag) {
    const h = Vue.h;

    if (activeTab === "basic") {
      return h("div", { class: "tab-pane" }, [
        h("div", { class: "form-group" }, [
          h("label", "显示名称 *"),
          h("input", {
            type: "text",
            value: form.displayName,
            onInput: (e) => (form.displayName = e.target.value),
            placeholder: "例如：Java 后端开发",
          }),
        ]),
        h("div", { class: "form-row" }, [
          h("div", { class: "form-group" }, [
            h("label", "分类 *"),
            h(
              "select",
              {
                value: form.category,
                onChange: (e) => (form.category = e.target.value),
              },
              [
                h("option", { value: "" }, "请选择分类"),
                ...categories.map((c) =>
                  h("option", { value: c.name, key: c.name }, c.displayName)
                ),
              ]
            ),
          ]),
          h("div", { class: "form-group" }, [
            h("label", "版本"),
            h("input", {
              type: "text",
              value: form.version,
              onInput: (e) => (form.version = e.target.value),
              placeholder: "1.0.0",
            }),
          ]),
        ]),
        h("div", { class: "form-row" }, [
          h("div", { class: "form-group" }, [
            h("label", "排序"),
            h("input", {
              type: "number",
              value: form.sortOrder,
              onInput: (e) => (form.sortOrder = Number(e.target.value) || 0),
            }),
          ]),
          h("div", { class: "form-group checkbox" }, [
            h("label", [
              h("input", {
                type: "checkbox",
                checked: form.published,
                onChange: (e) => (form.published = e.target.checked),
              }),
              h("span", "立即发布（前台可见）"),
            ]),
          ]),
        ]),
      ]);
    }

    if (activeTab === "media") {
      return h("div", { class: "tab-pane" }, [
        h("div", { class: "form-group" }, [
          h("label", "封面图 URL"),
          h("input", {
            type: "text",
            value: form.coverImage,
            onInput: (e) => (form.coverImage = e.target.value),
            placeholder: "https://...",
          }),
        ]),
        h("div", { class: "form-group" }, [
          h("label", "简短描述"),
          h("textarea", {
            rows: "3",
            value: form.summary,
            onInput: (e) => (form.summary = e.target.value),
            placeholder: "在卡片上显示的简短介绍",
          }),
        ]),
        h("div", { class: "form-row" }, [
          h("div", { class: "form-group" }, [
            h("label", "作者名称"),
            h("input", {
              type: "text",
              value: form.author,
              onInput: (e) => (form.author = e.target.value),
              placeholder: "作者名",
            }),
          ]),
          h("div", { class: "form-group" }, [
            h("label", "作者头像 URL"),
            h("input", {
              type: "text",
              value: form.authorAvatar,
              onInput: (e) => (form.authorAvatar = e.target.value),
              placeholder: "https://...",
            }),
          ]),
        ]),
        h("div", { class: "form-group" }, [
          h("label", "作者主页 URL"),
          h("input", {
            type: "text",
            value: form.authorUrl,
            onInput: (e) => (form.authorUrl = e.target.value),
            placeholder: "https://...",
          }),
        ]),
      ]);
    }

    if (activeTab === "content") {
      return h("div", { class: "tab-pane" }, [
        h("div", { class: "form-group" }, [
          h("label", "内容类型 *"),
          h("div", { class: "radio-group" }, [
            h("label", { class: "radio-item" }, [
              h("input", {
                type: "radio",
                name: "sourceType",
                value: "markdown",
                checked: form.sourceType === "markdown",
                onChange: () => (form.sourceType = "markdown"),
              }),
              h("span", "Markdown（支持代码高亮）"),
            ]),
            h("label", { class: "radio-item" }, [
              h("input", {
                type: "radio",
                name: "sourceType",
                value: "external",
                checked: form.sourceType === "external",
                onChange: () => (form.sourceType = "external"),
              }),
              h("span", "外链"),
            ]),
          ]),
        ]),
        form.sourceType === "markdown"
          ? h("div", { class: "form-group" }, [
              h("label", "Markdown 内容"),
              h("div", { class: "editor-wrapper" }, [
                h("textarea", {
                  rows: "12",
                  class: "code-editor",
                  value: form.content,
                  onInput: (e) => (form.content = e.target.value),
                  placeholder:
                    "# 标题\n\n描述内容...\n\n```java\npublic class Hello { }\n```",
                }),
              ]),
            ])
          : h("div", { class: "form-group" }, [
              h("label", "外部链接 URL"),
              h("input", {
                type: "text",
                value: form.externalUrl,
                onInput: (e) => (form.externalUrl = e.target.value),
                placeholder: "https://...",
              }),
            ]),
        h("div", { class: "form-group" }, [
          h("label", "标签"),
          h("div", { class: "tags-input" }, [
            ...form.tags.map((tag) =>
              h("span", { class: "tag-chip", key: tag }, [
                h("span", tag + " "),
                h(
                  "button",
                  {
                    onClick: () => removeTag(tag),
                    innerHTML: icon("close"),
                  }
                ),
              ])
            ),
            h("input", {
              type: "text",
              value: form.tagInput,
              onInput: (e) => (form.tagInput = e.target.value),
              onKeyup: (e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  addTag();
                }
              },
              placeholder: "输入标签后按回车",
              style: "flex:1;min-width:100px;border:none;outline:none;",
            }),
          ]),
        ]),
        h("div", { class: "form-group" }, [
          h("label", "安装命令"),
          h("input", {
            type: "text",
            value: form.installCommand,
            onInput: (e) => (form.installCommand = e.target.value),
            placeholder: "例如：npm install my-package",
          }),
        ]),
      ]);
    }

    if (activeTab === "stats") {
      return h("div", { class: "tab-pane" }, [
        h("div", { class: "form-row" }, [
          h("div", { class: "form-group" }, [
            h("label", "下载量"),
            h("input", {
              type: "number",
              value: form.downloads,
              onInput: (e) => (form.downloads = Number(e.target.value) || 0),
              min: "0",
            }),
          ]),
          h("div", { class: "form-group" }, [
            h("label", "评分 (0-5)"),
            h("input", {
              type: "number",
              value: form.rating,
              onInput: (e) =>
                (form.rating = Math.min(5, Math.max(0, Number(e.target.value) || 0))),
              min: "0",
              max: "5",
              step: "0.1",
            }),
          ]),
        ]),
        h("div", { class: "form-row" }, [
          h("div", { class: "form-group" }, [
            h("label", "评分人数"),
            h("input", {
              type: "number",
              value: form.ratingCount,
              onInput: (e) => (form.ratingCount = Number(e.target.value) || 0),
              min: "0",
            }),
          ]),
          h("div", { class: "form-group" }, [
            h("label", "收藏数"),
            h("input", {
              type: "number",
              value: form.favorites,
              onInput: (e) => (form.favorites = Number(e.target.value) || 0),
              min: "0",
            }),
          ]),
        ]),
      ]);
    }

    return h("div");
  }

  // ==================== SkillUploadDialog ====================
  const SkillUploadDialog = defineComponent({
    name: "SkillUploadDialog",
    props: {
      categories: { type: Array, default: () => [] },
    },
    emits: ["close", "uploaded"],
    setup(props, { emit }) {
      const fileInputRef = ref(null);
      const file = ref(null);
      const uploading = ref(false);
      const progress = ref(0);

      const form = reactive({
        displayName: "",
        version: "1.0.0",
        category: "",
        summary: "",
        coverImage: "",
        tags: "",
        author: "",
      });

      function onFileChange(e) {
        if (e.target.files && e.target.files[0]) {
          file.value = e.target.files[0];
          if (!form.displayName) {
            form.displayName = file.value.name.replace(/\.zip$/i, "");
          }
        }
      }

      function onDragOver(e) {
        e.preventDefault();
      }

      function onDrop(e) {
        e.preventDefault();
        if (e.dataTransfer && e.dataTransfer.files[0]) {
          file.value = e.dataTransfer.files[0];
          if (!form.displayName) {
            form.displayName = file.value.name.replace(/\.zip$/i, "");
          }
        }
      }

      function pickFile() {
        if (fileInputRef.value) fileInputRef.value.click();
      }

      async function handleUpload() {
        if (!file.value) {
          alert("请选择 ZIP 文件");
          return;
        }
        if (!form.displayName.trim()) {
          alert("请输入 Skill 名称");
          return;
        }
        if (!form.category) {
          alert("请选择分类");
          return;
        }

        uploading.value = true;
        progress.value = 0;

        const formData = new FormData();
        formData.append("file", file.value);
        formData.append("displayName", form.displayName);
        formData.append("version", form.version);
        formData.append("category", form.category);
        if (form.summary) formData.append("summary", form.summary);
        if (form.coverImage) formData.append("coverImage", form.coverImage);
        if (form.tags) formData.append("tags", form.tags);
        if (form.author) formData.append("author", form.author);

        try {
          await axios.post(`${API_BASE}/skills/upload`, formData, {
            headers: { "Content-Type": "multipart/form-data" },
            onUploadProgress: (e) => {
              if (e.total) {
                progress.value = Math.round((e.loaded / e.total) * 100);
              }
            },
          });
          emit("uploaded");
        } catch (err) {
          const msg = err?.response?.data?.message || err.message || "未知错误";
          alert("上传失败: " + msg);
        } finally {
          uploading.value = false;
        }
      }

      return () =>
        h(
          "div",
          {
            class: "dialog-overlay",
            onClick: (e) => {
              if (e.target === e.currentTarget) emit("close");
            },
          },
          [
            h("div", { class: "dialog", style: "max-width: 560px;" }, [
              h("div", { class: "dialog-header" }, [
                h("h3", "ZIP 上传 Skill"),
                h(
                  "button",
                  {
                    class: "close-btn",
                    onClick: () => emit("close"),
                    innerHTML: icon("close"),
                  }
                ),
              ]),
              h("div", { class: "dialog-body" }, [
                // Drop zone
                h(
                  "div",
                  {
                    class: ["drop-zone", { hasFile: !!file.value }],
                    onDragover,
                    onDrop,
                    onClick: pickFile,
                  },
                  file.value
                    ? [
                        h("div", { class: "file-info" }, [
                          h("div", { class: "file-icon", innerHTML: icon("package") }),
                          h("div", { class: "file-name" }, file.value.name),
                          h("div", { class: "file-size" }, formatFileSize(file.value.size)),
                        ]),
                      ]
                    : [
                        h("div", { class: "drop-hint" }, [
                          h("div", { class: "drop-icon" }, "📁"),
                          h("div", "点击选择或拖拽 ZIP 文件到此处"),
                          h("div", { class: "drop-sub" }, "ZIP 内必须包含 index.html"),
                        ]),
                      ]
                ),
                h("input", {
                  ref: fileInputRef,
                  type: "file",
                  accept: ".zip",
                  style: "display:none",
                  onChange: onFileChange,
                }),
                // Form fields
                h("div", { class: "form-group" }, [
                  h("label", "Skill 名称 *"),
                  h("input", {
                    type: "text",
                    value: form.displayName,
                    onInput: (e) => (form.displayName = e.target.value),
                    placeholder: "显示名称",
                  }),
                ]),
                h("div", { class: "form-row" }, [
                  h("div", { class: "form-group" }, [
                    h("label", "版本"),
                    h("input", {
                      type: "text",
                      value: form.version,
                      onInput: (e) => (form.version = e.target.value),
                      placeholder: "1.0.0",
                    }),
                  ]),
                  h("div", { class: "form-group" }, [
                    h("label", "分类 *"),
                    h(
                      "select",
                      {
                        value: form.category,
                        onChange: (e) => (form.category = e.target.value),
                      },
                      [
                        h("option", { value: "" }, "请选择分类"),
                        ...props.categories.map((c) =>
                          h("option", { value: c.name, key: c.name }, c.displayName)
                        ),
                      ]
                    ),
                  ]),
                ]),
                h("div", { class: "form-row" }, [
                  h("div", { class: "form-group" }, [
                    h("label", "作者"),
                    h("input", {
                      type: "text",
                      value: form.author,
                      onInput: (e) => (form.author = e.target.value),
                      placeholder: "作者名称",
                    }),
                  ]),
                  h("div", { class: "form-group" }, [
                    h("label", "标签（逗号分隔）"),
                    h("input", {
                      type: "text",
                      value: form.tags,
                      onInput: (e) => (form.tags = e.target.value),
                      placeholder: "tag1,tag2,tag3",
                    }),
                  ]),
                ]),
                h("div", { class: "form-group" }, [
                  h("label", "简短描述"),
                  h("textarea", {
                    rows: "2",
                    value: form.summary,
                    onInput: (e) => (form.summary = e.target.value),
                    placeholder: "在卡片上显示的简短介绍",
                  }),
                ]),
                h("div", { class: "form-group" }, [
                  h("label", "封面图 URL"),
                  h("input", {
                    type: "text",
                    value: form.coverImage,
                    onInput: (e) => (form.coverImage = e.target.value),
                    placeholder: "https://...",
                  }),
                ]),
                // Progress
                uploading.value
                  ? h("div", { class: "progress" }, [
                      h("div", {
                        class: "progress-bar",
                        style: `width:${progress.value}%`,
                      }),
                      h("span", { class: "progress-text" }, progress.value + "%"),
                    ])
                  : null,
              ]),
              h("div", { class: "dialog-footer" }, [
                h(
                  "button",
                  {
                    class: "btn btn-secondary",
                    disabled: uploading.value,
                    onClick: () => emit("close"),
                  },
                  "取消"
                ),
                h(
                  "button",
                  {
                    class: "btn btn-primary",
                    disabled: uploading.value || !file.value,
                    onClick: handleUpload,
                  },
                  uploading.value ? "上传中..." : "上传并创建"
                ),
              ]),
            ]),
          ]
        );
    },
  });

  // ==================== CategoryManager ====================
  const CategoryManager = defineComponent({
    name: "CategoryManager",
    props: {
      categories: { type: Array, default: () => [] },
    },
    emits: ["close", "changed"],
    setup(props, { emit }) {
      const localCategories = ref([]);
      const saving = ref(false);
      const showForm = ref(false);
      const isEdit = ref(false);
      const editingName = ref("");

      const form = reactive({
        displayName: "",
        description: "",
        icon: "📂",
        color: "#3b82f6",
        sortOrder: 0,
        parent: "",
      });

      const dragIndex = ref(null);

      async function fetchCategories() {
        try {
          const res = await axios.get(`${API_BASE}/categories`);
          localCategories.value = res.data || [];
        } catch (err) {
          console.error(err);
        }
      }

      function openCreate() {
        isEdit.value = false;
        editingName.value = "";
        form.displayName = "";
        form.description = "";
        form.icon = "📂";
        form.color = "#3b82f6";
        form.sortOrder = 0;
        form.parent = "";
        showForm.value = true;
      }

      function openEdit(cat) {
        isEdit.value = true;
        editingName.value = cat.name;
        form.displayName = cat.displayName || "";
        form.description = cat.description || "";
        form.icon = cat.icon || "📂";
        form.color = cat.color || "#3b82f6";
        form.sortOrder = cat.sortOrder ?? 0;
        form.parent = cat.parent || "";
        showForm.value = true;
      }

      function cancelForm() {
        showForm.value = false;
      }

      async function saveCategory() {
        if (!form.displayName.trim()) {
          alert("请输入分类名称");
          return;
        }
        saving.value = true;
        try {
          const name = isEdit.value
            ? editingName.value
            : generateName(form.displayName) + "-" + Date.now().toString(36) + "-";

          const payload = {
            apiVersion: "skill-showcase.halo.run/v1alpha1",
            kind: "SkillCategory",
            metadata: isEdit.value ? { name } : { generateName: generateName(form.displayName) + "-" + Date.now().toString(36) + "-" },
            spec: {
              displayName: form.displayName,
              description: form.description,
              icon: form.icon,
              color: form.color,
              sortOrder: Number(form.sortOrder) || 0,
              parent: form.parent || "",
            },
          };

          if (isEdit.value) {
            await axios.put(`${API_BASE}/categories/${name}`, payload);
          } else {
            await axios.post(`${API_BASE}/categories`, payload);
          }
          showForm.value = false;
          await fetchCategories();
          emit("changed");
        } catch (err) {
          const msg = err?.response?.data?.message || err.message || "未知错误";
          alert("保存失败: " + msg);
        } finally {
          saving.value = false;
        }
      }

      async function deleteCategory(name) {
        if (!confirm(`确定删除分类 "${name}" 吗？`)) return;
        try {
          await axios.delete(`${API_BASE}/categories/${name}`);
          await fetchCategories();
          emit("changed");
        } catch (err) {
          alert("删除失败");
        }
      }

      async function moveUp(index) {
        if (index <= 0) return;
        const list = localCategories.value;
        const current = list[index];
        const prev = list[index - 1];
        const tmpOrder = current.sortOrder;
        current.sortOrder = prev.sortOrder;
        prev.sortOrder = tmpOrder;

        try {
          await axios.put(`${API_BASE}/categories/${current.name}`, {
            ...current,
            spec: { ...current.spec, sortOrder: current.sortOrder },
          });
          await axios.put(`${API_BASE}/categories/${prev.name}`, {
            ...prev,
            spec: { ...prev.spec, sortOrder: prev.sortOrder },
          });
          await fetchCategories();
        } catch (err) {
          alert("排序失败");
        }
      }

      async function moveDown(index) {
        const list = localCategories.value;
        if (index >= list.length - 1) return;
        const current = list[index];
        const next = list[index + 1];
        const tmpOrder = current.sortOrder;
        current.sortOrder = next.sortOrder;
        next.sortOrder = tmpOrder;

        try {
          await axios.put(`${API_BASE}/categories/${current.name}`, {
            ...current,
            spec: { ...current.spec, sortOrder: current.sortOrder },
          });
          await axios.put(`${API_BASE}/categories/${next.name}`, {
            ...next,
            spec: { ...next.spec, sortOrder: next.sortOrder },
          });
          await fetchCategories();
        } catch (err) {
          alert("排序失败");
        }
      }

      function onDragStart(index) {
        dragIndex.value = index;
      }

      async function onDrop(targetIndex) {
        if (dragIndex.value === null || dragIndex.value === targetIndex) return;
        const list = localCategories.value;
        const [removed] = list.splice(dragIndex.value, 1);
        list.splice(targetIndex, 0, removed);

        for (let i = 0; i < list.length; i++) {
          list[i].sortOrder = i * 10;
        }

        dragIndex.value = null;

        try {
          for (const cat of list) {
            await axios.put(`${API_BASE}/categories/${cat.name}`, {
              ...cat,
              spec: { ...cat.spec, sortOrder: cat.sortOrder },
            });
          }
          await fetchCategories();
        } catch (err) {
          alert("排序失败");
          await fetchCategories();
        }
      }

      onMounted(fetchCategories);

      return () => {
        const h = Vue.h;

        return h(
          "div",
          {
            class: "dialog-overlay",
            onClick: (e) => {
              if (e.target === e.currentTarget) emit("close");
            },
          },
          [
            h("div", { class: "dialog", style: "max-width: 600px;" }, [
              h("div", { class: "dialog-header" }, [
                h("h3", "分类管理"),
                h(
                  "button",
                  {
                    class: "close-btn",
                    onClick: () => emit("close"),
                    innerHTML: icon("close"),
                  }
                ),
              ]),
              h("div", { class: "dialog-body" }, [
                showForm.value
                  ? h("div", { class: "form-area" }, [
                      h("div", { class: "form-row" }, [
                        h("div", { class: "form-group" }, [
                          h("label", "分类名称 *"),
                          h("input", {
                            type: "text",
                            value: form.displayName,
                            onInput: (e) => (form.displayName = e.target.value),
                            placeholder: "显示名称",
                          }),
                        ]),
                        h("div", { class: "form-group" }, [
                          h("label", "描述"),
                          h("input", {
                            type: "text",
                            value: form.description,
                            onInput: (e) => (form.description = e.target.value),
                            placeholder: "可选",
                          }),
                        ]),
                      ]),
                      h("div", { class: "form-row" }, [
                        h("div", { class: "form-group" }, [
                          h("label", "图标"),
                          h("input", {
                            type: "text",
                            value: form.icon,
                            onInput: (e) => (form.icon = e.target.value),
                            placeholder: "emoji",
                            class: "icon-input",
                          }),
                        ]),
                        h("div", { class: "form-group" }, [
                          h("label", "颜色"),
                          h("input", {
                            type: "color",
                            value: form.color,
                            onInput: (e) => (form.color = e.target.value),
                            class: "color-input",
                          }),
                        ]),
                      ]),
                      h("div", { class: "form-row" }, [
                        h("div", { class: "form-group" }, [
                          h("label", "父分类"),
                          h(
                            "select",
                            {
                              value: form.parent,
                              onChange: (e) => (form.parent = e.target.value),
                            },
                            [
                              h("option", { value: "" }, "无（顶级分类）"),
                              ...localCategories
                                .filter((c) => c.name !== editingName.value)
                                .map((c) =>
                                  h("option", { value: c.name, key: c.name }, c.displayName)
                                ),
                            ]
                          ),
                        ]),
                        h("div", { class: "form-group" }, [
                          h("label", "排序"),
                          h("input", {
                            type: "number",
                            value: form.sortOrder,
                            onInput: (e) =>
                              (form.sortOrder = Number(e.target.value) || 0),
                            placeholder: "0",
                          }),
                        ]),
                      ]),
                      h("div", { class: "form-actions" }, [
                        h(
                          "button",
                          {
                            class: "btn btn-primary",
                            disabled: saving.value,
                            onClick: saveCategory,
                          },
                          saving.value ? "保存中..." : "保存"
                        ),
                        h(
                          "button",
                          { class: "btn btn-secondary", onClick: cancelForm },
                          "取消"
                        ),
                      ]),
                    ])
                  : [
                      h("div", { class: "toolbar" }, [
                        h("span", { class: "hint" }, "共 " + localCategories.value.length + " 个分类"),
                        h(
                          "button",
                          { class: "btn btn-primary", onClick: openCreate },
                          "+ 新增分类"
                        ),
                      ]),
                      localCategories.value.length > 0
                        ? h(
                            "div",
                            { class: "category-list" },
                            localCategories.value.map((cat, index) =>
                              h(
                                "div",
                                {
                                  class: "category-row",
                                  style: `border-left-color:${cat.color || "#3b82f6"}`,
                                  draggable: "true",
                                  onDragStart: () => onDragStart(index),
                                  onDragOver: (e) => e.preventDefault(),
                                  onDrop: () => onDrop(index),
                                },
                                [
                                  h(
                                    "div",
                                    { class: "cat-drag", innerHTML: icon("dragHandle") }
                                  ),
                                  h("div", { class: "cat-icon" }, cat.icon || "📂"),
                                  h("div", { class: "cat-info" }, [
                                    h("div", { class: "cat-name" }, cat.displayName),
                                    cat.description
                                      ? h("div", { class: "cat-desc" }, cat.description)
                                      : null,
                                  ]),
                                  h("div", { class: "cat-actions" }, [
                                    h(
                                      "button",
                                      {
                                        class: "btn-icon",
                                        onClick: () => moveUp(index),
                                        disabled: index === 0,
                                        title: "上移",
                                        innerHTML: icon("chevronUp"),
                                      }
                                    ),
                                    h(
                                      "button",
                                      {
                                        class: "btn-icon",
                                        onClick: () => moveDown(index),
                                        disabled: index >= localCategories.value.length - 1,
                                        title: "下移",
                                        innerHTML: icon("chevronDown"),
                                      }
                                    ),
                                    h(
                                      "button",
                                      {
                                        class: "btn-link",
                                        onClick: () => openEdit(cat),
                                      },
                                      "编辑"
                                    ),
                                    h(
                                      "button",
                                      {
                                        class: "btn-link danger",
                                        onClick: () => deleteCategory(cat.name),
                                      },
                                      "删除"
                                    ),
                                  ]),
                                ]
                              )
                            )
                          )
                        : h("div", { class: "empty" }, '暂无分类，点击"新增分类"添加'),
                    ],
              ]),
            ]),
          ]);
      };
    },
  });

  // ==================== SkillListTab ====================
  const SkillListTab = defineComponent({
    name: "SkillListTab",
    setup() {
      const skills = ref([]);
      const categories = ref([]);
      const loading = ref(false);
      const searchQuery = ref("");
      const categoryFilter = ref("");
      const showEditor = ref(false);
      const showUploader = ref(false);
      const showCategoryMgr = ref(false);
      const editingSkill = ref(null);
      const selectedSkills = ref(new Set());
      const quickEditStat = ref(null);
      const quickEditValue = ref("");

      const filteredSkills = computed(() => {
        return skills.value.filter((s) => {
          if (categoryFilter.value && s.category !== categoryFilter.value) return false;
          if (searchQuery.value) {
            const q = searchQuery.value.toLowerCase();
            return (
              (s.displayName || "").toLowerCase().includes(q) ||
              (s.summary || "").toLowerCase().includes(q) ||
              (s.tags || []).some((t) => t.toLowerCase().includes(q))
            );
          }
          return true;
        });
      });

      const allSelected = computed(() => {
        return (
          filteredSkills.value.length > 0 &&
          filteredSkills.value.every((s) => selectedSkills.value.has(s.name))
        );
      });

      const someSelected = computed(() => {
        return filteredSkills.value.some((s) => selectedSkills.value.has(s.name));
      });

      async function fetchSkills() {
        loading.value = true;
        try {
          const res = await axios.get(`${API_BASE}/skills`);
          skills.value = res.data || [];
        } catch (err) {
          console.error("加载 Skills 失败", err);
        } finally {
          loading.value = false;
        }
      }

      async function fetchCategories() {
        try {
          const res = await axios.get(`${API_BASE}/categories`);
          categories.value = res.data || [];
        } catch (err) {
          console.error("加载分类失败", err);
        }
      }

      function toggleSelectAll() {
    if (allSelected.value) {
      selectedSkills.value = new Set();
    } else {
      selectedSkills.value = new Set(filteredSkills.value.map((s) => s.name));
    }
  }

  function toggleSelect(name) {
    const newSet = new Set(selectedSkills.value);
    if (newSet.has(name)) {
      newSet.delete(name);
    } else {
      newSet.add(name);
    }
    selectedSkills.value = newSet;
  }

      function openCreate() {
        editingSkill.value = null;
        showEditor.value = true;
      }

      function openEdit(skill) {
        editingSkill.value = skill;
        showEditor.value = true;
      }

      async function handleDelete(name) {
        if (!confirm(`确定删除 Skill "${name}" 吗？`)) return;
        try {
          await axios.delete(`${API_BASE}/skills/${name}`);
          const newSet = new Set(selectedSkills.value);
          newSet.delete(name);
          selectedSkills.value = newSet;
          await fetchSkills();
        } catch (err) {
          alert("删除失败");
        }
      }

      async function handleTogglePublish(skill) {
        try {
          const payload = {
            apiVersion: "skill-showcase.halo.run/v1alpha1",
            kind: "Skill",
            metadata: { name: skill.name, version: skill.version || 1 },
            spec: { ...skill, published: !skill.published },
          };
          await axios.put(`${API_BASE}/skills/${skill.name}`, payload);
          await fetchSkills();
        } catch (err) {
          alert("更新失败");
        }
      }

      async function batchAction(action) {
        if (selectedSkills.value.size === 0) {
          alert("请先选择要操作的 Skill");
          return;
        }

        if (action === "delete") {
          if (!confirm(`确定删除选中的 ${selectedSkills.value.size} 个 Skill 吗？`)) return;
        }

        try {
          for (const name of selectedSkills.value) {
            const skill = skills.value.find((s) => s.name === name);
            if (!skill) continue;

            if (action === "delete") {
              await axios.delete(`${API_BASE}/skills/${name}`);
            } else if (action === "publish") {
              const payload = {
                apiVersion: "skill-showcase.halo.run/v1alpha1",
                kind: "Skill",
                metadata: { name: skill.name, version: skill.version || 1 },
                spec: { ...skill, published: true },
              };
              await axios.put(`${API_BASE}/skills/${name}`, payload);
            } else if (action === "unpublish") {
              const payload = {
                apiVersion: "skill-showcase.halo.run/v1alpha1",
                kind: "Skill",
                metadata: { name: skill.name, version: skill.version || 1 },
                spec: { ...skill, published: false },
              };
              await axios.put(`${API_BASE}/skills/${name}`, payload);
            }
          }
          selectedSkills.value = new Set();
          await fetchSkills();
        } catch (err) {
          alert("批量操作失败");
        }
      }

      function openQuickEdit(skill, field) {
        quickEditStat.value = { name: skill.name, field, current: skill[field] };
        quickEditValue.value = String(skill[field] ?? 0);
      }

      async function saveQuickEdit() {
        if (!quickEditStat.value) return;
        const { name, field } = quickEditStat.value;
        const skill = skills.value.find((s) => s.name === name);
        if (!skill) return;

        const numVal = Number(quickEditValue.value);
        if (isNaN(numVal) || numVal < 0) {
          alert("请输入有效的数字");
          return;
        }

        try {
          const payload = {
            apiVersion: "skill-showcase.halo.run/v1alpha1",
            kind: "Skill",
            metadata: { name: skill.name, version: skill.version || 1 },
            spec: { ...skill, [field]: numVal },
          };
          await axios.put(`${API_BASE}/skills/${skill.name}`, payload);
          await fetchSkills();
        } catch (err) {
          alert("更新失败");
        } finally {
          quickEditStat.value = null;
        }
      }

      function cancelQuickEdit() {
        quickEditStat.value = null;
      }

      function getCategoryName(catName) {
        const cat = categories.value.find((c) => c.name === catName);
        return cat ? cat.displayName : catName;
      }

      onMounted(async () => {
        await Promise.all([fetchSkills(), fetchCategories()]);
      });

      return () => {
        const h = Vue.h;

        return h("div", { class: "skill-list-tab" }, [
          // Toolbar
          h("div", { class: "toolbar" }, [
            h("div", { class: "toolbar-left" }, [
              h("h2", { class: "title" }, "Skill 管理"),
            ]),
            h("div", { class: "toolbar-right" }, [
              // Category filter
              h(
                "select",
                {
                  value: categoryFilter.value,
                  onChange: (e) => (categoryFilter.value = e.target.value),
                  class: "filter-select",
                },
                [
                  h("option", { value: "" }, "全部分类"),
                  ...categories.value.map((c) =>
                    h("option", { value: c.name, key: c.name }, c.displayName)
                  ),
                ]
              ),
              // Search
              h("div", { class: "search-input-wrapper" }, [
                h("span", { innerHTML: icon("search") }),
                h("input", {
                  type: "text",
                  value: searchQuery.value,
                  onInput: (e) => (searchQuery.value = e.target.value),
                  placeholder: "搜索 Skill...",
                  class: "search-input",
                }),
              ]),
              // Category management
              h(
                "button",
                {
                  class: "btn btn-secondary",
                  onClick: () => (showCategoryMgr.value = true),
                },
                "分类管理"
              ),
              // Upload
              h(
                "button",
                {
                  class: "btn btn-secondary",
                  onClick: () => (showUploader.value = true),
                },
                "ZIP 上传"
              ),
              // New
              h(
                "button",
                {
                  class: "btn btn-primary",
                  onClick: openCreate,
                  innerHTML: icon("plus") + " 新增 Skill",
                }
              ),
            ]),
          ]),

          // Batch operations bar
          someSelected.value
            ? h("div", { class: "batch-bar" }, [
                h("span", `已选择 ${selectedSkills.value.size} 项`),
                h(
                  "button",
                  {
                    class: "btn btn-sm",
                    onClick: () => batchAction("publish"),
                  },
                  "批量发布"
                ),
                h(
                  "button",
                  {
                    class: "btn btn-sm",
                    onClick: () => batchAction("unpublish"),
                  },
                  "批量取消发布"
                ),
                h(
                  "button",
                  {
                    class: "btn btn-sm danger",
                    onClick: () => batchAction("delete"),
                  },
                  "批量删除"
                ),
                h(
                  "button",
                  {
                    class: "btn btn-sm",
                    onClick: () => { selectedSkills.value = new Set(); },
                  },
                  "取消选择"
                ),
              ])
            : null,

          // Table
          h("div", { class: "table-container" }, [
            h("table", { class: "skill-table" }, [
              h("thead", [
                h("tr", [
                  h("th", { style: "width:40px" }, [
                    h("input", {
                      type: "checkbox",
                      checked: allSelected.value,
                      onChange: toggleSelectAll,
                    }),
                  ]),
                  h("th", { style: "width:60px" }, "封面"),
                  h("th", "名称"),
                  h("th", "版本"),
                  h("th", "分类"),
                  h("th", "类型"),
                  h("th", "标签"),
                  h("th", { style: "width:80px" }, "排序"),
                  h("th", { style: "width:60px" }, "发布"),
                  h("th", { style: "width:80px" }, "下载"),
                  h("th", { style: "width:80px" }, "评分"),
                  h("th", { style: "width:80px" }, "收藏"),
                  h("th", { style: "width:160px" }, "操作"),
                ]),
              ]),
              h("tbody", [
                loading.value
                  ? h("tr", [
                      h(
                        "td",
                        { colspan: 13, class: "loading-cell" },
                        "加载中..."
                      ),
                    ])
                  : filteredSkills.value.length === 0
                  ? h("tr", [
                      h("td", { colspan: 13, class: "empty-cell" }, [
                        h("div", { class: "empty-state" }, [
                          h("div", { class: "empty-icon" }, "📦"),
                          h("div", { class: "empty-title" }, "暂无 Skill"),
                          h(
                            "div",
                            { class: "empty-desc" },
                            '点击右上角"新增 Skill"或"ZIP 上传"来创建第一个 Skill'
                          ),
                        ]),
                      ]),
                    ])
                  : filteredSkills.value.map((s) => {
                      const meta = getSkillTypeMeta(s.sourceType);
                      return h("tr", { key: s.name }, [
                        h("td", [
                          h("input", {
                            type: "checkbox",
                            checked: selectedSkills.value.has(s.name),
                            onChange: () => toggleSelect(s.name),
                          }),
                        ]),
                        h("td", [
                          s.coverImage
                            ? h("div", { class: "cover-thumb" }, [
                                h("img", {
                                  src: s.coverImage,
                                  alt: s.displayName,
                                }),
                              ])
                            : h(
                                "div",
                                { class: "cover-thumb placeholder" },
                                (s.displayName || "?").charAt(0)
                              ),
                        ]),
                        h("td", [
                          h("div", { class: "skill-name" }, s.displayName),
                          s.summary
                            ? h("div", { class: "skill-summary" }, s.summary)
                            : null,
                        ]),
                        h("td", [
                          h("span", { class: "version-badge" }, s.version || "1.0.0"),
                        ]),
                        h("td", [
                          h("span", { class: "category-tag" }, getCategoryName(s.category)),
                        ]),
                        h("td", [
                          h(
                            "span",
                            {
                              class: "source-badge",
                              style: `background-color:${meta.color}`,
                            },
                            meta.text
                          ),
                        ]),
                        h("td", [
                          h("div", { class: "tags-cell" }, [
                            ...(s.tags || []).slice(0, 3).map((tag) =>
                              h("span", { class: "tag", key: tag }, tag)
                            ),
                            (s.tags || []).length > 3
                              ? h(
                                  "span",
                                  { class: "tag more" },
                                  "+" + (s.tags.length - 3)
                                )
                              : null,
                          ]),
                        ]),
                        h("td", String(s.sortOrder ?? 0)),
                        h("td", [
                          h("label", { class: "switch" }, [
                            h("input", {
                              type: "checkbox",
                              checked: s.published,
                              onChange: () => handleTogglePublish(s),
                            }),
                            h("span", { class: "slider" }),
                          ]),
                        ]),
                        // Downloads - quick edit
                        h("td", [
                          quickEditStat.value && quickEditStat.value.name === s.name && quickEditStat.value.field === "downloads"
                            ? h("div", { class: "quick-edit" }, [
                                h("input", {
                                  type: "number",
                                  value: quickEditValue.value,
                                  onInput: (e) => (quickEditValue.value = e.target.value),
                                  onKeyup: (e) => {
                                    if (e.key === "Enter") saveQuickEdit();
                                    if (e.key === "Escape") cancelQuickEdit();
                                  },
                                  autoFocus: "true",
                                  min: "0",
                                }),
                                h(
                                  "button",
                                  { class: "btn-link", onClick: saveQuickEdit },
                                  "✓"
                                ),
                                h(
                                  "button",
                                  { class: "btn-link", onClick: cancelQuickEdit },
                                  "✕"
                                ),
                              ])
                            : h(
                                "span",
                                {
                                  class: "stat-cell",
                                  onClick: () => openQuickEdit(s, "downloads"),
                                  title: "点击编辑",
                                },
                                [
                                  h("span", { innerHTML: icon("download") }),
                                  " " + (s.downloads ?? 0),
                                ]
                              ),
                        ]),
                        // Rating - quick edit
                        h("td", [
                          quickEditStat.value && quickEditStat.value.name === s.name && quickEditStat.value.field === "rating"
                            ? h("div", { class: "quick-edit" }, [
                                h("input", {
                                  type: "number",
                                  value: quickEditValue.value,
                                  onInput: (e) => (quickEditValue.value = e.target.value),
                                  onKeyup: (e) => {
                                    if (e.key === "Enter") saveQuickEdit();
                                    if (e.key === "Escape") cancelQuickEdit();
                                  },
                                  autoFocus: "true",
                                  min: "0",
                                  max: "5",
                                  step: "0.1",
                                }),
                                h(
                                  "button",
                                  { class: "btn-link", onClick: saveQuickEdit },
                                  "✓"
                                ),
                                h(
                                  "button",
                                  { class: "btn-link", onClick: cancelQuickEdit },
                                  "✕"
                                ),
                              ])
                            : h(
                                "span",
                                {
                                  class: "stat-cell",
                                  onClick: () => openQuickEdit(s, "rating"),
                                  title: "点击编辑",
                                },
                                [
                                  h("span", { innerHTML: icon("star") }),
                                  " " + (s.rating ?? 0),
                                ]
                              ),
                        ]),
                        // Favorites - quick edit
                        h("td", [
                          quickEditStat.value && quickEditStat.value.name === s.name && quickEditStat.value.field === "favorites"
                            ? h("div", { class: "quick-edit" }, [
                                h("input", {
                                  type: "number",
                                  value: quickEditValue.value,
                                  onInput: (e) => (quickEditValue.value = e.target.value),
                                  onKeyup: (e) => {
                                    if (e.key === "Enter") saveQuickEdit();
                                    if (e.key === "Escape") cancelQuickEdit();
                                  },
                                  autoFocus: "true",
                                  min: "0",
                                }),
                                h(
                                  "button",
                                  { class: "btn-link", onClick: saveQuickEdit },
                                  "✓"
                                ),
                                h(
                                  "button",
                                  { class: "btn-link", onClick: cancelQuickEdit },
                                  "✕"
                                ),
                              ])
                            : h(
                                "span",
                                {
                                  class: "stat-cell",
                                  onClick: () => openQuickEdit(s, "favorites"),
                                  title: "点击编辑",
                                },
                                [
                                  h("span", { innerHTML: icon("heart") }),
                                  " " + (s.favorites ?? 0),
                                ]
                              ),
                        ]),
                        // Actions
                        h("td", [
                          h("div", { class: "actions" }, [
                            h(
                              "button",
                              {
                                class: "btn-link",
                                onClick: () => openEdit(s),
                                innerHTML: icon("edit") + " 编辑",
                              }
                            ),
                            s.sourceType === "zip" && s.previewUrl
                              ? h(
                                  "button",
                                  {
                                    class: "btn-link",
                                    onClick: () => window.open(s.previewUrl, "_blank"),
                                    innerHTML: icon("eye") + " 预览",
                                  }
                                )
                              : null,
                            h(
                              "button",
                              {
                                class: "btn-link danger",
                                onClick: () => handleDelete(s.name),
                                innerHTML: icon("trash") + " 删除",
                              }
                            ),
                          ]),
                        ]),
                      ]);
                    })
              ]),
            ]),
          ]),

          // Editor dialog
          showEditor.value
            ? h(SkillEditorDialog, {
                skill: editingSkill.value,
                categories: categories.value,
                onClose: () => (showEditor.value = false),
                onSaved: async () => {
                  await fetchSkills();
                  await fetchCategories();
                  showEditor.value = false;
                },
              })
            : null,

          // Upload dialog
          showUploader.value
            ? h(SkillUploadDialog, {
                categories: categories.value,
                onClose: () => (showUploader.value = false),
                onUploaded: async () => {
                  await fetchSkills();
                  await fetchCategories();
                  showUploader.value = false;
                },
              })
            : null,

          // Category manager dialog
          showCategoryMgr.value
            ? h(CategoryManager, {
                categories: categories.value,
                onClose: () => (showCategoryMgr.value = false),
                onChange: fetchCategories,
              })
            : null,
        ]);
      };
    },
  });

  return {
    routes: [],
    extensionPoints: {
      "plugin:self:tabs:create": () => [
        {
          id: "skill-showcase:skill-management",
          label: "Skill 管理",
          component: Vue.markRaw(SkillListTab),
        },
      ],
    },
  };
}(Vue, axios);
