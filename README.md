# AICodeTransformer

一个强大的 IntelliJ IDEA 插件，利用 AI 技术帮助开发者进行智能代码转换、优化和生成。通过自定义 Prompt 模板和快捷键，实现一键式 AI 代码处理。

## ✨ 功能特性

### 🤖 AI 代码转换
- **多模型支持**: 支持 OpenAI GPT、Claude、Gemini 等主流 AI 模型
- **智能转换**: 代码格式转换、语言转换、命名规范转换
- **上下文感知**: 自动识别代码类型和编程语言

### 📝 Prompt 模板管理
- **内置模板**: 提供驼峰命名转换、对象转换、JSON格式化等常用模板
- **自定义模板**: 支持创建个性化的 AI 指令模板
- **变量系统**: 支持 `${selectedCode}`、`${fileName}` 等内置变量
- **模板分类**: 按功能分类管理，便于查找和使用

### 🔧 JSON 格式化工具
- **智能修复**: 自动修复常见的 JSON 语法错误
- **特殊字符处理**: 清理转义字符和不可见字符
- **自动补全**: 智能补全缺失的括号、引号和逗号
- **格式选择**: 支持压缩和美化两种输出格式
- **错误诊断**: 提供详细的错误信息和修复建议

### ⚡ 快捷键操作
- **自定义绑定**: 为每个模板设置专属快捷键

### 🔧 灵活配置
- **模型配置**: 支持多个 AI 模型配置，可切换使用
- **参数调优**: Temperature、Max Tokens 等参数可调
- **安全存储**: API 密钥安全加密存储

### 🚀 性能优化
- **异步处理**: 非阻塞的 AI 调用，不影响 IDE 使用
- **智能缓存**: 缓存常用结果，提升响应速度
- **限流保护**: 防止 API 过度调用
- **性能监控**: 实时监控 API 调用性能

## 📦 安装方式

### 方式一：从源码构建

1. **克隆项目**
```bash
git clone https://github.com/your-repo/AICodeTransformer.git
cd AICodeTransformer
```

2. **构建插件**
```bash
./gradlew buildPlugin
```

3. **安装插件**
   - 打开 IntelliJ IDEA
   - 进入 `File` → `Settings` → `Plugins`
   - 点击齿轮图标 → `Install Plugin from Disk...`
   - 选择 `build/distributions/AICodeTransformer-1.0-SNAPSHOT.zip`
   - 重启 IDE

### 方式二：开发模式运行

```bash
./gradlew runIde
```

## ⚙️ 配置指南

### 1. 打开设置页面

- **方式一**: `File` → `Settings` → `Tools` → `AI Code Transformer`
- **方式二**: 在代码编辑器中右键 → `AI代码转换器` → 选择功能

### 2. AI 模型配置

#### 添加新的 AI 模型配置

1. 在设置页面点击「模型配置」标签页
2. 点击「+」按钮添加新配置
3. 填写配置信息：

**基本信息**
- **配置名称**: 为配置起一个易识别的名称
- **描述**: 配置的详细说明（可选）
- **模型类型**: 选择 AI 模型类型（OpenAI、Claude 等）
- **启用状态**: 是否启用此配置

**API 配置**
- **API Base URL**: AI 服务的 API 地址
  - OpenAI: `https://api.openai.com/v1`
  - Claude: `https://api.anthropic.com`
  - 自定义服务: 根据实际情况填写
- **API Key**: 您的 API 密钥（安全加密存储）
- **模型名称**: 具体的模型名称
  - GPT-4: `gpt-4` 或 `gpt-4-turbo`
  - GPT-3.5: `gpt-3.5-turbo`
  - Claude: `claude-3-sonnet-20240229`

**参数配置**
- **Temperature**: 控制输出随机性（0.0-2.0，推荐 0.7）
- **Max Tokens**: 最大输出长度（推荐 2048-4096）

**连接配置**
- **连接超时**: API 连接超时时间（秒）
- **读取超时**: API 响应超时时间（秒）
- **重试次数**: 请求失败时的重试次数

#### 配置示例

**OpenAI GPT-4 配置**
```
配置名称: GPT-4
模型类型: OpenAI
API Base URL: https://api.openai.com/v1
API Key: sk-your-api-key-here
模型名称: gpt-4
Temperature: 0.7
Max Tokens: 4096
```

**Claude 配置**
```
配置名称: Claude-3-Sonnet
模型类型: Claude
API Base URL: https://api.anthropic.com
API Key: your-claude-api-key
模型名称: claude-3-sonnet-20240229
Temperature: 0.7
Max Tokens: 4096
```

### 3. Prompt 模板配置

在「Prompt 模板」标签页中，您可以：

#### 内置模板

插件提供以下内置模板：

1. **驼峰命名转换** (`Ctrl+Alt+C`)
   - 将选中的文本转换为驼峰命名格式
   - 适用于变量名、方法名的命名规范转换

2. **对象转换** (`Ctrl+Alt+V`)
   - 生成 Java 对象之间的转换方法
   - 支持 VO/DTO/DOMAIN/ENTITY 转换
   - 基于字段分析的逐字段显式转换

3. **JSON 格式化** (`Ctrl+Alt+J`)
   - 智能格式化和修复 JSON 文本
   - 自动清理特殊字符和转义序列
   - 智能补全缺失的括号和引号
   - 修复常见的 JSON 语法错误
   - 支持压缩和美化两种输出格式
   - 提供详细的错误诊断和修复建议

#### 自定义模板

**创建新模板**
1. 点击「添加」按钮
2. 填写模板信息：
   - **名称**: 模板显示名称
   - **描述**: 模板功能说明
   - **分类**: 选择模板分类
   - **内容**: 编写 Prompt 内容
   - **快捷键**: 设置快捷键（可选）

**模板变量**

支持以下内置变量：
- `${selectedCode}` - 当前选中的代码
- `${fileName}` - 当前文件名
- `${language}` - 当前文件的编程语言
- `${projectName}` - 项目名称
- `${filePath}` - 当前文件路径
- `${className}` - 当前类名
- `${methodName}` - 当前方法名
- `${packageName}` - 当前包名

**模板示例**
```
请将以下 ${language} 代码转换为更优雅的形式：

```${language}
${selectedCode}
```

要求：
1. 优化代码结构和可读性
2. 遵循 ${language} 最佳实践
3. 添加必要的注释
4. 保持原有功能不变

文件：${fileName}
```

#### 模板管理

- **搜索**: 使用搜索框快速查找模板
- **分类筛选**: 按分类组织和筛选模板
- **导入导出**: 支持模板的批量导入导出
- **编辑**: 修改现有模板的任何属性
- **删除**: 移除不需要的模板
- **启用/禁用**: 控制模板的可用状态

## 🎯 使用方法

### 1. 基本使用流程

1. **选择代码**: 在编辑器中选中要处理的代码
2. **触发功能**: 使用快捷键或右键菜单
3. **选择模板**: 选择合适的 AI 模板（如果需要）
4. **查看结果**: AI 处理完成后查看结果
5. **应用更改**: 确认后应用到代码中

### 2. 快捷键操作

#### 内置快捷键
- `Ctrl+Alt+T` - 快速代码转换
- `Ctrl+Alt+O` - 代码优化
- `Ctrl+Alt+E` - 代码解释
- `Ctrl+Alt+C` - 驼峰命名转换
- `Ctrl+Alt+V` - 对象转换
- `Ctrl+Alt+J` - JSON 格式化

#### 自定义快捷键
- 在模板配置中为每个模板设置专属快捷键
- 支持 `Ctrl+Alt+字母` 和 `Ctrl+Shift+字母` 格式
- 自动检测快捷键冲突

### 3. 右键菜单操作

在编辑器中右键选择「AI代码转换器」，可以看到：
- 所有可用的模板列表
- 按分类组织的模板
- 模板的描述和快捷键信息

### 4. 使用技巧

**提高转换质量**
- 选择合适的 AI 模型（GPT-4 通常效果更好）
- 调整 Temperature 参数（0.3-0.7 之间通常效果较好）
- 提供清晰的代码选择和上下文
- 使用自定义模板优化 Prompt

**性能优化**
- 合理设置 Max Tokens 避免过长响应
- 利用缓存机制减少重复调用
- 在网络较慢时适当增加超时时间

## 🛠️ 技术架构

### 核心组件

- **ConfigurationService**: 配置管理服务
- **PromptTemplateService**: 模板管理服务
- **AIModelService**: AI 模型调用服务
- **ActionService**: 快捷键动作服务
- **ExecutionService**: 执行流程协调
- **CodeReplacementService**: 代码替换服务
- **StatusService**: 状态反馈服务
- **ErrorHandlingService**: 错误处理服务

### 技术特性

- **异步处理**: 所有 AI 调用均为异步，不阻塞 UI
- **线程安全**: 使用并发安全的数据结构
- **错误恢复**: 智能重试和故障转移机制
- **性能监控**: 实时监控 API 调用性能
- **安全存储**: API 密钥使用 IntelliJ 平台安全存储

## 🔧 开发信息

- **开发语言**: Kotlin
- **构建工具**: Gradle
- **IDE 平台**: IntelliJ Platform
- **最低 IDE 版本**: IntelliJ IDEA 2024.1+
- **JDK 版本**: JDK 17+

### 项目结构

```
src/main/kotlin/cn/suso/aicodetransformer/
├── action/                 # 动作类
├── model/                  # 数据模型
├── service/                # 服务接口和实现
├── ui/                     # 用户界面
│   ├── dialog/            # 对话框
│   └── settings/          # 设置面板
└── debug/                 # 调试工具
```

## ❓ 常见问题

### Q: API 请求失败怎么办？
A: 请检查以下项目：
- API 密钥是否正确且有效
- API 配额是否充足
- 网络连接是否稳定
- 模型名称是否正确

### Q: 如何提高代码转换质量？
A: 建议：
- 选择合适的 AI 模型（longcat 通常效果更好）
- 调整 Temperature 参数（0.3-0.7 之间通常效果较好）
- 提供清晰的转换要求和上下文
- 使用自定义 Prompt 模板优化提示词

### Q: 如何自定义 Prompt 模板？
A: 在设置页面的「Prompt 模板」标签页中：
1. 点击「添加」按钮创建新模板
2. 设置模板名称和描述
3. 编写 Prompt 内容，可使用内置变量如 `${selectedCode}`, `${language}`, `${fileName}` 等
4. 设置快捷键（可选）
5. 保存并在转换时选择使用

### Q: 快捷键不生效怎么办？
A: 请检查：
- 快捷键格式是否正确
- 是否与 IDE 内置快捷键冲突
- 模板是否已启用
- 是否选中了代码（某些功能需要选中代码）

### Q: 如何查看调试信息？
A: 可以使用以下方式：
- 查看 IDE 日志：`Help` → `Show Log in Explorer`
- 使用调试功能：`Tools` → `AI代码转换器` → `日志管理`

## 🔄 更新日志

### v1.0.0
- ✅ 支持多种 AI 模型配置
- ✅ 提供完整的设置界面
- ✅ 支持 Prompt 模板自定义
- ✅ 内置驼峰命名和对象转换模板
- ✅ 性能优化和限流保护
- ✅ 完整的日志记录系统

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进这个项目。

## 📞 技术支持

如果您在使用过程中遇到问题，请：

1. 查看本文档的常见问题部分
2. 检查 IDE 的日志文件（`Help` → `Show Log in Explorer`）
3. 确认插件配置是否正确
4. 提交 Issue 到项目仓库

---

**注意**: 使用本插件需要有效的 AI 服务 API 密钥。请确保遵守相关服务的使用条款和隐私政策。