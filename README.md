# Halo Plugin Skill Showcase

在 [Halo](https://halo.run) 博客中管理和展示技能作品集（Skill），支持 **Markdown 文档**、**ZIP 演示站 / 项目包** 和 **外链** 三种内容形态，并内置一套完整的前端展示页面。

> 在线示例：<https://boommanpro.cn/skill>

## 功能特性

- **三种内容类型**
  - `markdown`：直接填写/导入 Markdown 内容，前台渲染为图文文档
  - `zip`：上传 ZIP 包，支持两种子类型
    - 网站包：包含 `index.html`，前台直接打开为可交互演示站（带 iframe 预览）
    - 项目包（skillhub 风格）：以 `SKILL.md → README.md → 首个 .md` 作为入口，前台以 VSCode 风格文件树 + 代码查看器浏览
  - `external`：外链，前台一键跳转访问
- **管理端可视化**：Skill 管理 + 分类管理，内置在 Halo 后台
  - 增删改查、发布/下架、搜索、分类筛选
  - ZIP 类型支持**在线更新**（重新上传替换，slug 不变）
  - 所有类型均支持**预览**
  - 标签可直接内联编辑，分类可配置显示名
- **前台展示页**
  - 技能卡片列表（分类筛选 + 搜索 + 排序）
  - 详情页：文件树 + Markdown/代码渲染 + 下载 + 一键复制安装命令
  - 数据统计页、相关技能推荐
  - 亮/暗色模式自适应、响应式布局
- **示例集成**：可直接挂在主题下作为自定义页面（如下方示例网站的 `/skill`），也可使用插件自带的 `/showcase` 页面

## 环境要求

- Halo `>= 2.20.0`
- Java `21`
- pnpm `9`（构建前端）

## 快速开始

### 1. 安装插件

在 Halo 后台 **插件 → 已安装 → 安装**，上传构建产物 `halo-plugin-skill-showcase-{version}.jar`。

> JAR 需在 `build/libs/` 下构建，详见下方「本地构建」。

### 2. 使用管理端

安装后进入 Halo 后台，在插件管理页的 **Skill 展示** 标签下操作：

- **Skill 管理**：创建/编辑/删除 Skill，选择分类、填写标签与描述、控制发布状态
- **Category 管理**：维护分类（显示名、图标等）

#### 创建 Skill

| 类型 | 操作 |
|---|---|
| Markdown | 在编辑器中填写或粘贴 Markdown 内容 |
| ZIP | 上传 ZIP 文件（需在根目录或单层顶层目录包含 `index.html` 或 Markdown 入口文件，≤ 50MB），可自定义 slug；后续可「更新」重新上传 |
| 外链 | 填写外部 URL 地址 |

### 3. 前台展示

插件提供两种前台方案：

**方案 A：插件自带页面（默认）**
访问 `{站点地址}/showcase` 即可使用内置的 SkillHub 列表 / 详情 / 统计页面。

**方案 B：集成到主题自定义页面（推荐作为站点一级页面）**
在主题的 `theme.yaml` 中注册 `skill` 自定义模板，然后在 Halo 后台「页面」创建页面并选择对应模板即可。示例站点的实现方式即为该方案。
- 主题模板通过 `SkillFinder` Bean 读取已发布的 Skill 数据
- 详见本仓库配套主题 `halo-theme-chirpy` 的 `templates/skill.html`

> 注意：页面的自定义模板值存的是**模板名称**（如 `skill`），不是模板文件路径（如 `templates/skill.html`），配置错误会导致回退到默认模板。

## 示例项目

在线示例站点：<https://boommanpro.cn/skill>

演示内容包括：

- 技能卡片列表与分类筛选
- Skill 详情页（Markdown 渲染 + ZIP 演示站 iframe 预览）
- 数据统计概览

### ZIP 包结构示例

**网站演示包**（前台直接预览）：

```text
my-demo.zip
└── index.html          # 必须包含，前台作为 iframe 直接打开
    ├── style.css
    └── app.js
```

**项目/Skill 包**（前台文件树浏览）：

```text
my-skill.zip
├── SKILL.md            # 入口文件优先于 README.md
├── README.md
├── src/
│   └── main.py
└── requirements.txt
```

## 公开 API

前台数据通过 `/showcase-api` 公开访问：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/showcase-api/skills` | 已发布的技能列表（支持 `?category=` 筛选） |
| GET | `/showcase-api/skills/{name}` | 技能详情 |
| GET | `/showcase-api/skills/{name}/tree` | ZIP 技能文件树 |
| GET | `/showcase-api/skills/{name}/download` | 下载 ZIP（自动计数） |
| GET | `/showcase-api/skills/{name}/related` | 相关技能推荐 |
| GET | `/showcase-api/categories` | 分类列表 |
| GET | `/showcase-api/stats` | 平台统计 |

## 本地构建

```bash
# 构建前端 UI（pnpm 9）
cd ui
pnpm install
pnpm run build
cd ..

# 构建插件 JAR
./gradlew clean build
```

产物输出至 `build/libs/halo-plugin-skill-showcase-{version}.jar`。

### 本地开发

```bash
# 启动本地 Halo 并以开发模式加载插件（端口 8090，管理员 admin/admin）
./gradlew haloServer

# 前端热更新开发
cd ui && pnpm run dev
```

Halo 版本与本地端口在 `build.gradle` 的 `halo { version = '2.25.0'; port = 8090 }` 中配置。

## 项目结构

```text
.
├── src/main/java/run/halo/skillshowcase
│   ├── SkillShowcasePlugin.java       # 插件入口，注册 Skill / SkillCategory
│   ├── extension/                     # Skill、SkillCategory 自定义模型
│   ├── endpoint/                      # 管理端 API + 公开路由（/showcase、/showcase-api）
│   ├── service/                       # 业务逻辑、ZIP 存储与解压
│   ├── finder/SkillFinder.java        # 供主题模板读取数据
│   └── resources/showcase/            # 内置前台 SPA 页面
├── ui/                                # 管理端 Vue 组件（SkillListTab 等）
└── src/test/                          # 集成测试
```

## 参考

- [Halo 插件开发文档](https://docs.halo.run/developer-guide/plugin/introduction)
- 配套主题 [halo-theme-chirpy](https://github.com/AirboZH/halo-theme-chirpy)

## License

[MIT](./LICENSE)