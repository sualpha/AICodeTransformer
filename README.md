<div align="center">

# 🤖 AICodeTransformer

**一个强大的 IntelliJ IDEA 插件，利用 AI 技术帮助开发者进行智能代码转换、优化和生成**

[English](README_EN.md) | 简体中文

</div>

## 📖 简介

AICodeTransformer 是一个功能强大的 IntelliJ IDEA 插件，通过集成多种 AI 模型（OpenAI GPT、Claude、Gemini 等），为开发者提供智能代码格式化、优化和生成功能。通过自定义 Prompt 模板和快捷键绑定，实现一键式 AI 代码处理，大幅提升开发效率。

## ✨ 核心特性

### 🤖 多 AI 模型支持
- **OpenAI GPT 系列**: GPT-4, GPT-3.5-turbo
- **Anthropic Claude**: Claude-3-Sonnet, Claude-3-Haiku
- **自定义 API**: 支持兼容 OpenAI API 的本地模型

### 📝 智能模板系统
- **内置模板**: 变量命名生成、驼峰命名转换、对象转换、JSON 格式化、智能翻译等常用开发工具
- **自定义模板**: 支持创建个性化 AI 指令模板
- **变量系统**: 内置 `{{selectedCode}}`、`{{fileName}}`、`{{language}}` 等变量
- **分类管理**: 按功能分类，便于查找和使用

### 🧩 高效开发工具
- **对象转换**：基于字段分析，自动生成 Java 对象间的显式转换方法
- **驼峰命名转换**：支持任意格式字符串的命名风格切换
- **JSON 格式化**：智能修复与美化 JSON，自动补全、转义、纠错
- **智能翻译**：支持中英文注释与变量名智能互译

### 📝 AI生成Commit信息功能
- **智能分析**：自动分析文件变更和代码差异
- **规范格式**：生成符合Git提交规范的信息格式
- **批量处理**：支持多文件变更的智能汇总
- **自动提交**：可配置为生成后自动执行提交操作
- **自动推送**：支持提交后自动推送到远程仓库

### ⚡ 一键式 AI 代码处理
通过自定义 Prompt 模板与快捷键绑定，实现 **一键调用 AI 进行代码生成、转换、优化与格式化**，极大提升开发效率。

## 🚀 快速开始

### 安装方式

#### 方式一：从 JetBrains Marketplace 安装（推荐）

1. 打开 IntelliJ IDEA
2. 进入 `File` → `Settings` → `Plugins`
3. 搜索 "AICodeTransformer"
4. 点击 `Install` 安装
5. 重启 IDE

#### 方式二：从 GitHub Releases 安装（推荐）

1. 打开 Releases 页面：`https://github.com/sualpha/AICodeTransformer/releases`
2. 下载最新版本的插件文件（`AICodeTransformer-<version>.zip` 或 `.jar`）
3. 在 IDE 中：`File → Settings → Plugins` → 点击齿轮图标 → `Install Plugin from Disk...`
4. 选择刚下载的插件文件并确认安装
5. 重启 IDE

## ⚙️ 配置指南

### 1. 基本配置

打开设置页面：`File` → `Settings` → `Other Settings` → `AI Code Transformer`

### 2. AI 模型配置

#### OpenAI 配置示例
```
配置名称: GPT-4
模型类型: OpenAI
API Base URL: https://api.openai.com/v1
API Key: sk-your-api-key-here
模型名称: gpt-4
Temperature: 0.7
Max Tokens: 4096
```

#### Claude 配置示例
```
配置名称: Claude-3-Sonnet
模型类型: Claude
API Base URL: https://api.anthropic.com
API Key: your-claude-api-key
模型名称: claude-3-sonnet-20240229
Temperature: 0.7
Max Tokens: 4096
```

### 3. 模板变量说明

| 变量 | 描述 | 示例 |
|------|------|------|
| `{{selectedCode}}` | 当前选中的代码 | `public class Test {}` |
| `{{fileName}}` | 当前文件名 | `UserService.java` |
| `{{language}}` | 编程语言 | `Java` |
| `{{projectName}}` | 项目名称 | `MyProject` |
| `{{filePath}}` | 文件路径 | `src/main/java/User.java` |
| `{{className}}` | 当前类名 | `UserService` |
| `{{methodName}}` | 当前方法名 | `getUserById` |
| `{{packageName}}` | 包名 | `com.example.service` |

## 🎯 使用方法

### 基本操作流程

1. **选择代码**: 在编辑器中选中要处理的代码
2. **触发功能**: 使用快捷键或右键菜单（编辑器 → 右键 → `AI Code Transformer` → `动态模板`）
3. **选择模板**: 选择合适的 AI 模板
4. **查看结果**: AI 处理完成后查看结果
5. **应用更改**: 确认后应用到代码中

### 从 `prompts` 目录获取更多模板

- 也可从 GitHub 源码仓库的 [prompts 目录](https://github.com/sualpha/AICodeTransformer/tree/main/prompts) 获取示例模板，下载后导入使用。
- 在 IDE 中打开：`设置` → `Other Settings` → `AI Code Transformer`，点击左下角的 `导入模板` 按钮导入。

## ❓ 常见问题

<details>
<summary><strong>Q: API 请求失败怎么办？</strong></summary>

请检查以下项目：
- API 密钥是否正确且有效
- API 配额是否充足
- 网络连接是否稳定
- 模型名称是否正确
- 防火墙或代理设置是否影响连接
</details>

<details>
<summary><strong>Q: 如何提高代码转换质量？</strong></summary>

建议：
- 选择合适的 AI 模型（亲测longcat 效果更好）
- 提供清晰的转换要求和上下文
- 使用自定义 Prompt 模板优化提示词
- 选择合适的代码片段，避免过长或过短
</details>

<details>
<summary><strong>Q: 快捷键不生效怎么办？</strong></summary>

请检查：
- 快捷键格式是否正确
- 是否与 IDE 内置快捷键冲突
- 模板是否已启用
- 是否选中了代码（某些功能需要选中代码）
- 重启 IDE 后重试
</details>

<details>
<summary><strong>Q: 如何查看调试信息？</strong></summary>

可以使用以下方式：
- 查看 IDE 日志：`Help` → `Show Log in Explorer`
- 使用调试功能：`Tools` → `AI代码转换器` → `日志管理`
- 启用详细日志记录
</details>

## 📞 支持与反馈

如果您在使用过程中遇到问题或有任何建议，请通过以下方式联系我们：

- 📧 **邮箱**: www.suso@qq.com
- 🐛 **Bug 报告**: [GitHub Issues](https://github.com/sualpha/AICodeTransformer/issues)


## 🧭 贡献与建议 / Prompt 模板分享

欢迎通过 GitHub Issues 提交使用建议、功能需求、Bug 反馈，以及分享你在实际开发中验证有效的 Prompt 模板。

- 提交入口：GitHub Issues → https://github.com/sualpha/AICodeTransformer/issues
- 模板聚合：我们会不定期将优质模板汇总到 `prompts` 目录，并在插件中提供更便捷的使用入口

<div align="center">

**⭐ 如果这个项目对您有帮助，请给我们一个 Star！**

**🔗 [GitHub](https://github.com/sualpha/AICodeTransformer) | [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28570-aicodetransformer))**

**注意**: 使用本插件需要有效的 AI 服务 API 密钥。请确保遵守相关服务的使用条款和隐私政策。

</div>