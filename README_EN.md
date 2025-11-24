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
- **Built-in Templates**: Variable Name Generator, CamelCase conversion, object transformation, JSON formatting, Smart Translation, etc.
  - 🧠 **Comment-Driven Generator**: Transform inline comments into complete implementations while preserving method signatures
  - 🔄 **Object Conversion**: Generate conversion methods between Java objects with field-by-field explicit conversion based on field analysis
  - 📝 **CamelCase Conversion**: One-click variable naming format conversion, supporting arbitrary character strings
  - 📋 **JSON Formatting**: Intelligent JSON formatting tool: automatically handle special characters, complete missing symbols, fix syntax errors and beautify format
  - 🌐 **Smart Translation**: Intelligent Chinese-English code comments and variable name conversion
- **Custom Templates**: Support for creating personalized AI instruction templates
- **Variable System**: Built-in variables like `{{selectedCode}}`, `{{fileName}}`, `{{language}}`, etc.
- **Category Management**: Organized by function for easy searching and usage
- **Prompt AI Optimization**: Trigger the in-editor "AI Optimize" button to polish template name/description/content via the built-in preview dialog

### 📝 AI-Generated Commit Message Feature

**Feature Overview**:
The AI-generated commit message feature intelligently analyzes your code changes and automatically generates standardized Git commit messages, improving development efficiency and code management quality.
- **Intelligent Analysis**: Automatically analyze file changes and code differences
- **Standardized Format**: Generate commit messages that comply with Git commit conventions
- **Batch Processing**: Support intelligent summarization of multi-file changes
- **Auto Commit**: Configurable to automatically execute commits after generation
- **Auto Push**: Support automatic push to remote repository after commit

## 🚀 Quick Start

### Installation Methods

#### Method 1: Install from JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Go to `File` → `Settings` → `Plugins`
3. Search for "AICodeTransformer"
4. Click `Install`
5. Restart IDE

#### Method 2: Install from GitHub Releases (Recommended)

1. Open Releases page: `https://github.com/sualpha/AICodeTransformer/releases`
2. Download the latest plugin file (`AICodeTransformer-<version>.zip` or `.jar`)
3. In IDE: `File → Settings → Plugins` → click the gear icon → `Install Plugin from Disk...`
4. Select the downloaded file and confirm installation
5. Restart IDE

## ⚙️ Configuration Guide

###  Basic Configuration

Open settings: `File` → `Settings` → `Other Settings` → `AI Code Transformer`

## 🎯 Usage

### Basic Operation Flow

1. **Select Code**: Select the code to be processed in the editor
2. **Trigger Function**: Use shortcut keys or the editor right-click menu (Editor → Right-click → `AI Code Transformer` → `Dynamic Templates`)
3. **Choose Template**: Select appropriate AI template
4. **View Results**: Review results after AI processing
5. **Apply Changes**: Confirm and apply to code

### Get More Templates from `prompts` Directory

- You can also get sample templates from the GitHub source repository's [prompts folder](https://github.com/sualpha/AICodeTransformer/tree/main/prompts) and import them.
- In IDE: `Settings` → `Other Settings` → `AI Code Transformer`, click the `Import Templates` button (bottom-left) to load them.


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
- Choose appropriate AI model (LongCat tested to work better)
- Adjust Temperature parameter (0.3-0.7 usually works well)
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
- Whether code is selected (some functions require selected code)
- Restart IDE and retry
</details>

<details>
<summary><strong>Q: How to view debug information?</strong></summary>

You can use the following methods:
- View IDE logs: `Help` → `Show Log in Explorer`
- Use debug function: `Tools` → `AI Code Transformer` → `Log Management`
- Enable verbose logging
</details>

## 📞 Support & Feedback

If you encounter any issues or have suggestions during use, please contact us through:

- 📧 **Email**: www.suso@qq.com
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/sualpha/AICodeTransformer/issues)

## 🧭 Contribute Prompts and Suggestions

We welcome your feature suggestions, bug reports, and field-tested Prompt templates via GitHub Issues.

- Entry: GitHub Issues → https://github.com/sualpha/AICodeTransformer/issues
- Aggregation: High-quality templates will be periodically curated into `prompts` and made easier to use in the plugin

<div align="center">

**⭐ If this project helps you, please give us a Star!**

**🔗 [GitHub](https://github.com/sualpha/AICodeTransformer) | [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28570-aicodetransformer)**

**Note**: Using this plugin requires valid AI service API keys. Please ensure compliance with relevant service terms and privacy policies.

</div>