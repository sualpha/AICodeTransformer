<div align="center">

# 🤖 AICodeTransformer

**A powerful IntelliJ IDEA plugin that leverages AI technology to help developers with intelligent code transformation, optimization, and generation**

English | [简体中文](README.md)

</div>

## 📖 Introduction

AICodeTransformer is a powerful IntelliJ IDEA plugin that integrates multiple AI models (OpenAI GPT, Claude, Gemini, etc.) to provide developers with intelligent code transformation, optimization, and generation capabilities. Through custom Prompt templates and shortcut key bindings, it enables one-click AI code processing, significantly improving development efficiency.

## ✨ Core Features

### 🤖 Multi-AI Model Support
- **OpenAI GPT Series**: GPT-4, GPT-3.5-turbo
- **Anthropic Claude**: Claude-3-Sonnet, Claude-3-Haiku
- **Custom API**: Support for local models compatible with OpenAI API

### 📝 Intelligent Template System
- **Built-in Templates**: CamelCase conversion, object transformation, JSON formatting, etc.
  - 🔄 **Object Conversion**: Generate conversion methods between Java objects with field-by-field explicit conversion based on field analysis
  - 📝 **CamelCase Conversion**: One-click variable naming format conversion, supporting arbitrary character strings
  - 📋 **JSON Formatting**: Intelligent JSON formatting tool: automatically handle special characters, complete missing symbols, fix syntax errors and beautify format
- **Custom Templates**: Support for creating personalized AI instruction templates
- **Variable System**: Built-in variables like `{{selectedCode}}`, `{{fileName}}`, `{{language}}`, etc.
- **Category Management**: Organized by function for easy searching and usage

### ⚡ Efficient Operation Experience
- **Shortcut Key Binding**: Set dedicated shortcuts for each template
- **Context Menu**: Quick access to all AI functions
- **Asynchronous Processing**: Non-blocking AI calls that don't affect IDE usage
- **Smart Caching**: Improve response speed and reduce redundant calls

### 🛡️ Security & Performance
- **Secure Storage**: Encrypted storage of API keys
- **Rate Limiting**: Prevent excessive API calls
- **Performance Monitoring**: Real-time monitoring of API call performance
- **Error Recovery**: Intelligent retry and failover mechanisms

## 🚀 Quick Start

### System Requirements

- **IDE**: IntelliJ IDEA 2024.1+ / Android Studio / PyCharm / WebStorm and other JetBrains IDEs
- **JDK**: Java 17+

### Installation Methods

#### Method 1: Install from JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Go to `File` → `Settings` → `Plugins`
3. Search for "AICodeTransformer"
4. Click `Install`
5. Restart IDE

#### Method 2: Build from Source

```bash
# Clone the project
git clone https://github.com/sualpha/AICodeTransformer.git
cd AICodeTransformer

# Build the plugin
./gradlew buildPlugin

# Install the plugin
# In IDE: File → Settings → Plugins → Gear icon → Install Plugin from Disk
# Select build/distributions/AICodeTransformer-1.0.0-SNAPSHOT.zip
```

#### Method 3: Development Mode

```bash
# Start development environment
./gradlew runIde
```

## ⚙️ Configuration Guide

### 1. Basic Configuration

Open settings: `File` → `Settings` → `Tools` → `AI Code Transformer`

### 2. AI Model Configuration

#### OpenAI Configuration Example
```
Configuration Name: GPT-4
Model Type: OpenAI
API Base URL: https://api.openai.com/v1
API Key: sk-your-api-key-here
Model Name: gpt-4
Temperature: 0.7
Max Tokens: 4096
```

#### Claude Configuration Example
```
Configuration Name: Claude-3-Sonnet
Model Type: Claude
API Base URL: https://api.anthropic.com
API Key: your-claude-api-key
Model Name: claude-3-sonnet-20240229
Temperature: 0.7
Max Tokens: 4096
```

### 3. Template Variables

| Variable | Description | Example |
|----------|-------------|----------|
| `{{selectedCode}}` | Currently selected code | `public class Test {}` |
| `{{fileName}}` | Current file name | `UserService.java` |
| `{{language}}` | Programming language | `Java` |
| `{{projectName}}` | Project name | `MyProject` |
| `{{filePath}}` | File path | `src/main/java/User.java` |
| `{{className}}` | Current class name | `UserService` |
| `{{methodName}}` | Current method name | `getUserById` |
| `{{packageName}}` | Package name | `com.example.service` |

## 🎯 Usage

### Basic Operation Flow

1. **Select Code**: Select the code to be processed in the editor
2. **Trigger Function**: Use shortcuts or context menu
3. **Choose Template**: Select appropriate AI template
4. **View Results**: Check results after AI processing
5. **Apply Changes**: Confirm and apply to code

### Custom Template Example

```
Please convert the following {{language}} code to a more elegant form:

\`\`\`{{language}}
{{selectedCode}}
\`\`\`

Requirements:
1. Optimize code structure and readability
2. Follow {{language}} best practices
3. Add necessary comments
4. Keep original functionality unchanged

File: {{fileName}}
```

## 🛠️ Technical Architecture

### Core Components

```
src/main/kotlin/cn/suso/aicodetransformer/
├── action/                 # Actions and shortcut handling
├── model/                  # Data models and configuration
├── service/                # Core business services
│   ├── ConfigurationService      # Configuration management
│   ├── PromptTemplateService     # Template management
│   ├── AIModelService           # AI model invocation
│   ├── ActionService            # Action service
│   ├── ExecutionService         # Execution coordination
│   ├── CodeReplacementService   # Code replacement
│   └── StatusService            # Status management
├── ui/                     # User interface
│   ├── dialog/            # Dialog components
│   └── settings/          # Settings panel
└── debug/                 # Debug tools
```

### Technology Stack

- **Development Language**: Kotlin
- **Build Tool**: Gradle
- **UI Framework**: IntelliJ Platform SDK
- **Network Library**: Ktor Client
- **Serialization**: Kotlinx Serialization
- **Logging**: Logback
- **Testing**: JUnit 5 + Mockito

### Design Features

- **Asynchronous Processing**: All AI calls are asynchronous, non-blocking UI thread
- **Thread Safety**: Uses concurrent-safe data structures and services
- **Plugin Architecture**: Modular design, easy to extend and maintain
- **Configuration Driven**: Supports flexible configuration and customization

### Usage Tips

**Improve Conversion Quality**
- Choose appropriate AI model (LongCat-Flash-Thinking usually works better)
- Adjust Temperature parameter (0.3-0.7 range works well)
- Provide clear code selection and context
- Use custom templates to optimize prompts

**Performance Optimization**
- Set reasonable Max Tokens to avoid overly long responses
- Utilize caching mechanism to reduce redundant calls
- Increase timeout appropriately when network is slow
- Use batch processing to reduce API calls

## ❓ FAQ

<details>
<summary><strong>Q: What to do when API requests fail?</strong></summary>

Please check the following:
- Whether API key is correct and valid
- Whether API quota is sufficient
- Whether network connection is stable
- Whether model name is correct
- Whether firewall or proxy settings affect connection
</details>

<details>
<summary><strong>Q: How to improve code conversion quality?</strong></summary>

Suggestions:
- Choose appropriate AI model (GPT-4 usually works better)
- Adjust Temperature parameter (0.3-0.7 range usually works well)
- Provide clear conversion requirements and context
- Use custom Prompt templates to optimize prompts
- Select appropriate code snippets, avoid too long or too short
</details>

<details>
<summary><strong>Q: What to do when shortcuts don't work?</strong></summary>

Please check:
- Whether shortcut format is correct
- Whether it conflicts with IDE built-in shortcuts
- Whether template is enabled
- Whether code is selected (some functions require code selection)
- Restart IDE and try again
</details>

<details>
<summary><strong>Q: How to view debug information?</strong></summary>

You can use the following methods:
- View IDE logs: `Help` → `Show Log in Explorer`
- Use debug function: `Tools` → `AI Code Transformer` → `Log Management`
- Enable verbose logging
</details>

## 🔄 Changelog

For complete version update history, please refer to [CHANGELOG.md](CHANGELOG.md).

## 📞 Support & Feedback

If you encounter any issues or have suggestions during usage, please contact us through:

- 📧 **Email**: www.suso@qq.com
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/sualpha/AICodeTransformer/issues)

### Getting Help

1. Check the FAQ section in this document
2. Search for related issues in [GitHub Issues](https://github.com/sualpha/AICodeTransformer/issues)
3. Check IDE log files (`Help` → `Show Log in Explorer`)
4. Confirm plugin configuration is correct
5. Submit a new Issue with detailed information

---

<div align="center">

**⭐ If this project helps you, please give us a Star!**

**🔗 [GitHub](https://github.com/sualpha/AICodeTransformer) | [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28570-aicodetransformer))**

**Note**: Using this plugin requires valid AI service API keys. Please ensure compliance with relevant service terms and privacy policies.

</div>