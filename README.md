<div align="center">

# 🤖 AICodeTransformer

**一个强大的 IntelliJ IDEA 插件，利用 AI 技术帮助开发者进行智能代码转换、优化和生成**

[English](README_EN.md) | 简体中文

</div>

## 📖 简介

AICodeTransformer 是一个功能强大的 IntelliJ IDEA 插件，通过集成多种 AI 模型（OpenAI GPT、Claude、Gemini 等），为开发者提供智能代码转换、优化和生成功能。通过自定义 Prompt 模板和快捷键绑定，实现一键式 AI 代码处理，大幅提升开发效率。

## ✨ 核心特性

### 🤖 多 AI 模型支持
- **OpenAI GPT 系列**: GPT-4, GPT-3.5-turbo
- **Anthropic Claude**: Claude-3-Sonnet, Claude-3-Haiku
- **自定义 API**: 支持兼容 OpenAI API 的本地模型

### 📝 智能模板系统
- **内置模板**: 驼峰命名转换、对象转换、JSON 格式化等
  - 🔄 **对象转换**: 生成Java对象之间的转换方法，基于字段分析的逐字段显式转换
  - 📝 **驼峰命名转换**: 一键转换变量命名格式，支持任意字符长串的格式
  - 📋 **JSON格式化**: 智能JSON格式化工具：自动处理特殊字符、补全缺失符号、修复语法错误并美化格式
- **自定义模板**: 支持创建个性化 AI 指令模板
- **变量系统**: 内置 `{{selectedCode}}`、`{{fileName}}`、`{{language}}` 等变量
- **分类管理**: 按功能分类，便于查找和使用

### ⚡ 高效操作体验
- **快捷键绑定**: 为每个模板设置专属快捷键
- **右键菜单**: 快速访问所有 AI 功能
- **异步处理**: 非阻塞 AI 调用，不影响 IDE 使用
- **智能缓存**: 提升响应速度，减少重复调用

### 🛡️ 安全与性能
- **安全存储**: API 密钥加密存储
- **限流保护**: 防止 API 过度调用
- **性能监控**: 实时监控 API 调用性能
- **错误恢复**: 智能重试和故障转移机制

## 🚀 快速开始

### 系统要求

- **IDE**: IntelliJ IDEA 2024.1+ / Android Studio / PyCharm / WebStorm 等 JetBrains IDE
- **JDK**: Java 17+

### 安装方式

#### 方式一：从 JetBrains Marketplace 安装（推荐）

1. 打开 IntelliJ IDEA
2. 进入 `File` → `Settings` → `Plugins`
3. 搜索 "AICodeTransformer"
4. 点击 `Install` 安装
5. 重启 IDE

#### 方式二：从源码构建

```bash
# 克隆项目
git clone https://github.com/sualpha/AICodeTransformer.git
cd AICodeTransformer

# 构建插件
./gradlew buildPlugin

# 安装插件
# 在 IDE 中：File → Settings → Plugins → 齿轮图标 → Install Plugin from Disk
# 选择 build/distributions/AICodeTransformer-1.0.0-SNAPSHOT.zip
```

#### 方式三：开发模式运行

```bash
# 启动开发环境
./gradlew runIde
```

## ⚙️ 配置指南

### 1. 基本配置

打开设置页面：`File` → `Settings` → `Tools` → `AI Code Transformer`

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
2. **触发功能**: 使用快捷键或右键菜单
3. **选择模板**: 选择合适的 AI 模板
4. **查看结果**: AI 处理完成后查看结果
5. **应用更改**: 确认后应用到代码中

### 自定义模板示例

```
请将以下 {{language}} 代码转换为更优雅的形式：

\`\`\`{{language}}
{{selectedCode}}
\`\`\`

要求：
1. 优化代码结构和可读性
2. 遵循 {{language}} 最佳实践
3. 添加必要的注释
4. 保持原有功能不变

文件：{{fileName}}
```

## 🛠️ 技术架构

### 核心组件

```
src/main/kotlin/cn/suso/aicodetransformer/
├── action/                 # 动作和快捷键处理
├── model/                  # 数据模型和配置
├── service/                # 核心业务服务
│   ├── ConfigurationService      # 配置管理
│   ├── PromptTemplateService     # 模板管理
│   ├── AIModelService           # AI 模型调用
│   ├── ActionService            # 动作服务
│   ├── ExecutionService         # 执行协调
│   ├── CodeReplacementService   # 代码替换
│   └── StatusService            # 状态管理
├── ui/                     # 用户界面
│   ├── dialog/            # 对话框组件
│   └── settings/          # 设置面板
└── debug/                 # 调试工具
```

### 技术栈

- **开发语言**: Kotlin
- **构建工具**: Gradle
- **UI 框架**: IntelliJ Platform SDK
- **网络库**: Ktor Client
- **序列化**: Kotlinx Serialization
- **日志**: Logback
- **测试**: JUnit 5 + Mockito

### 设计特性

- **异步处理**: 所有 AI 调用均为异步，不阻塞 UI 线程
- **线程安全**: 使用并发安全的数据结构和服务
- **插件化架构**: 模块化设计，易于扩展和维护
- **配置驱动**: 支持灵活的配置和自定义

### 使用技巧

**提高转换质量**
- 选择合适的 AI 模型（LongCat-Flash-Thinking 通常效果更好）
- 调整 Temperature 参数（0.3-0.7 之间效果较好）
- 提供清晰的代码选择和上下文
- 使用自定义模板优化 Prompt

**性能优化**
- 合理设置 Max Tokens 避免过长响应
- 利用缓存机制减少重复调用
- 在网络较慢时适当增加超时时间
- 使用批量处理减少 API 调用次数

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
- 调整 Temperature 参数（0.3-0.7 之间通常效果较好）
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

## 🔄 更新日志

查看完整的版本更新历史，请参考 [CHANGELOG.md](CHANGELOG.md)。

## 📞 支持与反馈

如果您在使用过程中遇到问题或有任何建议，请通过以下方式联系我们：

- 📧 **邮箱**: www.suso@qq.com
- 🐛 **Bug 报告**: [GitHub Issues](https://github.com/sualpha/AICodeTransformer/issues)

### 获取帮助

1. 查看本文档的常见问题部分
2. 搜索 [GitHub Issues](https://github.com/sualpha/AICodeTransformer/issues) 中的相关问题
3. 检查 IDE 的日志文件（`Help` → `Show Log in Explorer`）
4. 确认插件配置是否正确
5. 提交新的 Issue 并提供详细信息

---

<div align="center">

**⭐ 如果这个项目对您有帮助，请给我们一个 Star！**

**🔗 [GitHub](https://github.com/sualpha/AICodeTransformer) | [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28570-aicodetransformer))**

**注意**: 使用本插件需要有效的 AI 服务 API 密钥。请确保遵守相关服务的使用条款和隐私政策。

</div>