package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.service.ActionService
import cn.suso.aicodetransformer.service.TemplateChangeListener
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.components.service
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*
import javax.swing.table.AbstractTableModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.SwingUtilities

/**
 * 快捷键绑定面板
 */
class ShortcutKeyBindingPanel : JPanel(), TemplateChangeListener {
    
    private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    
    private val tableModel = ShortcutTableModel()
    private val table = JBTable(tableModel)
    private val scrollPane = JBScrollPane(table)
    
    private val searchField = JBTextField()
    private val enabledOnlyCheckBox = JBCheckBox("仅显示启用的模板")
    
    init {
        layout = BorderLayout()
        setupUI()
        setupTable()
        loadData()
        
        // 注册模板变更监听器
        templateService.addTemplateChangeListener(this)
    }
    
    private fun setupUI() {
        // 顶部搜索面板
        val searchPanel = createSearchPanel()
        add(searchPanel, BorderLayout.NORTH)
        
        // 中间表格
        scrollPane.preferredSize = Dimension(600, 400)
        add(scrollPane, BorderLayout.CENTER)
        
        // 底部按钮面板已移除
    }
    
    private fun createSearchPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        val leftPanel = JPanel()
        leftPanel.add(JLabel("搜索:"))
        leftPanel.add(searchField)
        searchField.preferredSize = Dimension(200, searchField.preferredSize.height)
        
        val centerPanel = JPanel()
        centerPanel.add(enabledOnlyCheckBox)
        
        val rightPanel = JPanel()
        val clearButton = JButton("清除选中快捷键")
        clearButton.addActionListener { clearSelectedShortcuts() }
        clearButton.toolTipText = "清除选中模板的快捷键设置"
        rightPanel.add(clearButton)
        
        val helpButton = JButton("使用说明")
        helpButton.addActionListener { showHelpDialog() }
        helpButton.toolTipText = "查看快捷键设置的详细说明"
        rightPanel.add(helpButton)
        
        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(centerPanel, BorderLayout.CENTER)
        panel.add(rightPanel, BorderLayout.EAST)
        
        // 搜索监听器
        searchField.addActionListener { filterData() }
        enabledOnlyCheckBox.addActionListener { filterData() }
        
        return panel
    }
    
    private fun createButtonPanel(): JPanel? {
        // 按钮已移至搜索面板，不再需要底部按钮面板
        return null
    }
    
    private fun setupTable() {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.rowHeight = 25
        
        // 设置列宽
        val columnModel = table.columnModel
        columnModel.getColumn(0).preferredWidth = 200 // 模板名称
        columnModel.getColumn(1).preferredWidth = 300 // 描述
        columnModel.getColumn(2).preferredWidth = 150 // 快捷键
        columnModel.getColumn(3).preferredWidth = 80  // 启用状态
        
        // 设置快捷键列的编辑器和渲染器
        columnModel.getColumn(2).cellEditor = ShortcutCellEditor()
        columnModel.getColumn(2).cellRenderer = ShortcutCellRenderer()
        
        // 设置启用状态列的渲染器
        columnModel.getColumn(3).cellRenderer = BooleanCellRenderer()
    }
    
    private fun loadData() {
        val templates = templateService.getTemplates()
        tableModel.setData(templates)
    }
    
    private fun filterData() {
        val allTemplates = templateService.getTemplates()
        val searchText = searchField.text.trim().lowercase()
        val enabledOnly = enabledOnlyCheckBox.isSelected
        
        val filteredTemplates = allTemplates.filter { template ->
            val matchesSearch = searchText.isEmpty() || 
                template.name.lowercase().contains(searchText) ||
                (template.description?.lowercase()?.contains(searchText) == true)
            
            val matchesEnabled = !enabledOnly || template.enabled
            
            matchesSearch && matchesEnabled
        }
        
        tableModel.setData(filteredTemplates)
    }
    
    private fun clearSelectedShortcuts() {
        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            Messages.showInfoMessage(this, "请先选择要清除快捷键的模板", "提示")
            return
        }
        
        val result = Messages.showYesNoDialog(
            this,
            "确定要清除选中模板的快捷键吗？",
            "确认清除",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            selectedRows.forEach { row ->
                val template = tableModel.getTemplateAt(row)
                if (template != null) {
                    val updatedTemplate = template.copy(shortcutKey = null)
                    templateService.saveTemplate(updatedTemplate)
                }
            }
            loadData()
        }
    }
    

    

    

    
    private fun showHelpDialog() {
        val helpText = """
            快捷键设置使用说明：
            
            📌 设置快捷键：
            • 点击快捷键列的单元格
            • 按下您想要的快捷键组合（如 Ctrl+Alt+T）
            • 系统会自动检测并设置快捷键
            
            🚫 清除快捷键：
            • 方法1：点击快捷键单元格后按 ESC 键
            • 方法2：选中模板行，点击"清除选中快捷键"按钮
            • 方法3：在快捷键单元格中直接删除内容
            
            ⚠️ 冲突检测：
            • 红色背景：与其他快捷键冲突
            • 绿色背景：快捷键设置成功
            • 灰色背景：未设置快捷键
            
            💡 使用技巧：
            • 建议使用 Ctrl+Alt+字母 的组合
            • 避免与IDE内置快捷键冲突
            • 可以搜索模板名称快速定位
            • 支持批量清除多个模板的快捷键
        """.trimIndent()
        
        Messages.showInfoMessage(
            this,
            helpText,
            "快捷键设置帮助"
        )
    }
    
    /**
     * 快捷键表格模型
     */
    private class ShortcutTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("模板名称", "描述", "快捷键", "启用")
        private var templates = listOf<PromptTemplate>()
        
        fun setData(templates: List<PromptTemplate>) {
            this.templates = templates
            fireTableDataChanged()
        }
        
        fun getTemplateAt(row: Int): PromptTemplate? {
            return if (row in 0 until templates.size) templates[row] else null
        }
        
        override fun getRowCount(): Int = templates.size
        
        override fun getColumnCount(): Int = columnNames.size
        
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val template = templates[rowIndex]
            return when (columnIndex) {
                0 -> template.name
                1 -> template.description ?: ""
                2 -> template.shortcutKey ?: ""
                3 -> template.enabled
                else -> null
            }
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex == 2 // 只有快捷键列可编辑
        }
        
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 2) {
                val template = templates[rowIndex]
                val newShortcut = aValue as? String
                
                val updatedTemplate = template.copy(
                    shortcutKey = newShortcut?.takeIf { it.isNotBlank() }
                )
                
                val templateService = PromptTemplateServiceImpl.getInstance()
                
                // 验证模板（包括快捷键冲突检查）
                val validationError = templateService.validateTemplate(updatedTemplate)
                if (validationError != null) {
                    // 显示验证错误信息
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(
                            "快捷键设置失败：$validationError",
                            "快捷键冲突"
                        )
                    }
                    return
                }
                
                templateService.saveTemplate(updatedTemplate)
                // 更新本地数据以确保UI立即反映变化
                templates = templates.toMutableList().apply {
                    set(rowIndex, updatedTemplate)
                }
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }
    }
    
    /**
     * 快捷键单元格编辑器
     */
    private class ShortcutCellEditor : AbstractCellEditor(), TableCellEditor {
        private val shortcutField = ShortcutInputField()
        private var currentValue: String = ""
        private var currentTable: JTable? = null
        
        init {
            shortcutField.onShortcutChanged = { shortcut ->
                currentValue = shortcut
                // 立即停止编辑并保存
                stopCellEditing()
            }
        }
        
        override fun getTableCellEditorComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int
        ): Component {
            currentValue = value?.toString() ?: ""
            currentTable = table
            
            // 获取当前编辑的模板ID
            val templateId = if (table != null && table.model is ShortcutTableModel) {
                val tableModel = table.model as ShortcutTableModel
                tableModel.getTemplateAt(row)?.id
            } else null
            
            shortcutField.setCurrentTemplateId(templateId)
            shortcutField.setShortcut(currentValue)
            return shortcutField
        }
        
        override fun getCellEditorValue(): Any = currentValue
    }
    
    /**
     * 快捷键输入字段组件
     */
    private class ShortcutInputField : JTextField() {
        var onShortcutChanged: ((String) -> Unit)? = null
        private var isCapturing = false
        private val actionService: ActionService = service()
        private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
        private var currentTemplateId: String? = null
        
        init {
            isEditable = false
            text = "<点击设置快捷键，按ESC清除>"
            
            // 添加右键菜单
            setupContextMenu()
            
            addFocusListener(object : java.awt.event.FocusListener {
                override fun focusGained(e: java.awt.event.FocusEvent?) {
                    isCapturing = true
                    text = "按下快捷键组合或ESC清除..."
                    background = java.awt.Color(255, 255, 200) // 淡黄色背景表示正在捕获
                    toolTipText = "按下快捷键组合进行设置，或按ESC键清除快捷键"
                }
                
                override fun focusLost(e: java.awt.event.FocusEvent?) {
                    isCapturing = false
                    // 恢复到正确的背景色（根据冲突状态）
                    val currentShortcut = text
                    if (currentShortcut.isNotEmpty() && currentShortcut != "按下快捷键组合...") {
                        val conflictInfo = checkShortcutConflict(currentShortcut)
                        background = if (conflictInfo != null) {
                            java.awt.Color(255, 200, 200) // 红色背景表示冲突
                        } else {
                            java.awt.Color.WHITE
                        }
                    } else {
                        background = java.awt.Color.WHITE
                    }
                }
            })
            
            addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {}
                
                override fun keyPressed(e: KeyEvent?) {
                    if (e == null || !isCapturing) return
                    
                    // 忽略单独的修饰键
                    if (e.keyCode == KeyEvent.VK_CONTROL || 
                        e.keyCode == KeyEvent.VK_ALT || 
                        e.keyCode == KeyEvent.VK_SHIFT ||
                        e.keyCode == KeyEvent.VK_META) {
                        return
                    }
                    
                    // ESC键清除快捷键
                    if (e.keyCode == KeyEvent.VK_ESCAPE) {
                        setShortcut("")
                        onShortcutChanged?.invoke("")
                        return
                    }
                    
                    // 构建快捷键字符串
                    val shortcutText = buildShortcutText(e)
                    if (shortcutText.isNotEmpty()) {
                        // 检查快捷键冲突
                        val conflictInfo = checkShortcutConflict(shortcutText)
                        if (conflictInfo != null) {
                            // 显示冲突警告对话框
                            showConflictWarning(shortcutText, conflictInfo)
                        } else {
                            setShortcut(shortcutText)
                            background = java.awt.Color(200, 255, 200) // 绿色背景表示无冲突
                            toolTipText = null
                            onShortcutChanged?.invoke(shortcutText)
                        }
                    }
                }
                
                override fun keyReleased(e: KeyEvent?) {}
            })
        }
        
        private fun setupContextMenu() {
            val popupMenu = JPopupMenu()
            
            val clearItem = JMenuItem("清除快捷键")
            clearItem.addActionListener {
                setShortcut("")
                onShortcutChanged?.invoke("")
            }
            popupMenu.add(clearItem)
            
            val helpItem = JMenuItem("使用帮助")
            helpItem.addActionListener {
                val helpText = """
                    快捷键设置方法：
                    
                    1. 点击此输入框
                    2. 按下您想要的快捷键组合
                    3. 按ESC键可清除快捷键
                    4. 右键菜单可快速清除
                    
                    建议使用组合键如：
                    • Ctrl+Alt+字母
                    • Ctrl+Shift+字母
                """.trimIndent()
                
                Messages.showInfoMessage(
                    this@ShortcutInputField,
                    helpText,
                    "快捷键设置帮助"
                )
            }
            popupMenu.add(helpItem)
            
            componentPopupMenu = popupMenu
        }
        
        fun setShortcut(shortcut: String) {
            text = if (shortcut.isEmpty()) "<点击设置快捷键，按ESC清除>" else shortcut
            
            // 如果有快捷键，检查冲突状态来设置背景色
            if (shortcut.isNotEmpty()) {
                val conflictInfo = checkShortcutConflict(shortcut)
                if (conflictInfo != null) {
                    background = java.awt.Color(255, 200, 200) // 红色背景表示冲突
                    toolTipText = "警告：快捷键与 '$conflictInfo' 冲突。按ESC键清除快捷键"
                } else {
                    background = java.awt.Color(200, 255, 200) // 浅绿色背景表示无冲突
                    toolTipText = "快捷键设置成功。按ESC键可清除快捷键"
                }
            } else {
                background = java.awt.Color(245, 245, 245) // 浅灰色背景表示未设置
                toolTipText = "点击此处设置快捷键，按ESC键清除快捷键"
            }
        }
        
        fun setCurrentTemplateId(templateId: String?) {
            currentTemplateId = templateId
        }
        
        /**
         * 检查快捷键冲突
         * @return 冲突信息，null表示无冲突
         */
        private fun checkShortcutConflict(shortcut: String): String? {
            // 空或空白快捷键不存在冲突
            if (shortcut.isBlank()) {
                return null
            }
            
            // 检查与其他模板的冲突
            val conflictTemplate = templateService.getTemplates().find { 
                it.shortcutKey == shortcut && it.id != currentTemplateId 
            }
            if (conflictTemplate != null) {
                return "模板冲突: ${conflictTemplate.name}"
            }
            
            // 检查与IDE内置快捷键的冲突
            if (actionService.isShortcutInUse(shortcut)) {
                // 获取冲突的Action信息
                val conflictActions = getConflictingActions(shortcut)
                return if (conflictActions.isNotEmpty()) {
                    "IDE功能冲突: ${conflictActions.joinToString(", ")}"
                } else {
                    "IDE功能冲突"
                }
            }
            
            return null
        }
        
        /**
         * 获取冲突的Action名称
         */
        private fun getConflictingActions(shortcut: String): List<String> {
            return try {
                val keyStroke = actionService.parseShortcut(shortcut) ?: return emptyList()
                val keymap = KeymapManager.getInstance().activeKeymap
                val actionIds = keymap.getActionIds(keyStroke)
                
                actionIds.filter { !it.startsWith("AICodeTransformer.Template.") }
                    .map { actionId ->
                        // 尝试获取Action的显示名称
                        val action = com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionId)
                        action?.templatePresentation?.text ?: actionId
                    }
                    .take(3) // 最多显示3个冲突的Action
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        /**
         * 显示冲突警告
         */
        private fun showConflictWarning(shortcut: String, conflictInfo: String) {
            // 设置警告背景色
            background = java.awt.Color(255, 200, 200) // 淡红色
            text = "$shortcut (冲突: $conflictInfo)"
            
            // 显示工具提示
            toolTipText = "快捷键冲突: $conflictInfo\n按ESC清除或选择其他快捷键"
            
            // 可选：显示确认对话框
             SwingUtilities.invokeLater {
                 val result = Messages.showYesNoDialog(
                     null,
                     "快捷键 '$shortcut' 与 '$conflictInfo' 冲突。\n\n是否仍要使用此快捷键？",
                     "快捷键冲突警告",
                     "仍要使用",
                     "取消",
                     Messages.getWarningIcon()
                 )
                
                if (result == Messages.YES) {
                    // 用户确认使用冲突的快捷键
                    setShortcut(shortcut)
                    onShortcutChanged?.invoke(shortcut)
                } else {
                    // 用户取消，清除快捷键
                    setShortcut("")
                    onShortcutChanged?.invoke("")
                }
            }
        }
        
        private fun buildShortcutText(e: KeyEvent): String {
            val modifiers = mutableListOf<String>()
            
            if (e.isControlDown) modifiers.add("Ctrl")
            if (e.isAltDown) modifiers.add("Alt")
            if (e.isShiftDown) modifiers.add("Shift")
            if (e.isMetaDown) modifiers.add("Meta")
            
            val keyText = when (e.keyCode) {
                KeyEvent.VK_SPACE -> "Space"
                KeyEvent.VK_TAB -> "Tab"
                KeyEvent.VK_ENTER -> "Enter"
                KeyEvent.VK_BACK_SPACE -> "Backspace"
                KeyEvent.VK_DELETE -> "Delete"
                KeyEvent.VK_INSERT -> "Insert"
                KeyEvent.VK_HOME -> "Home"
                KeyEvent.VK_END -> "End"
                KeyEvent.VK_PAGE_UP -> "Page Up"
                KeyEvent.VK_PAGE_DOWN -> "Page Down"
                KeyEvent.VK_UP -> "Up"
                KeyEvent.VK_DOWN -> "Down"
                KeyEvent.VK_LEFT -> "Left"
                KeyEvent.VK_RIGHT -> "Right"
                in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "F${e.keyCode - KeyEvent.VK_F1 + 1}"
                else -> KeyEvent.getKeyText(e.keyCode)
            }
            
            // 至少需要一个修饰键（除了功能键）
            if (modifiers.isEmpty() && !keyText.startsWith("F")) {
                return ""
            }
            
            return if (modifiers.isNotEmpty()) {
                "${modifiers.joinToString("+")}+$keyText"
            } else {
                keyText
            }
        }
    }
    
    /**
     * 快捷键单元格渲染器
     */
    private class ShortcutCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            
            val shortcut = value?.toString()
            if (shortcut.isNullOrBlank()) {
                text = "<未设置>"
                foreground = java.awt.Color.GRAY
            } else {
                text = shortcut
                foreground = if (isSelected) table?.selectionForeground else table?.foreground
            }
            
            return component
        }
    }
    
    /**
     * 布尔值单元格渲染器
     */
    private class BooleanCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            
            val enabled = value as? Boolean ?: false
            text = if (enabled) "✓" else "✗"
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (enabled) java.awt.Color.GREEN else java.awt.Color.RED
            
            return component
        }
    }
    
    // TemplateChangeListener 实现
    override fun onTemplateAdded(template: PromptTemplate) {
        SwingUtilities.invokeLater {
            loadData()
        }
    }
    
    override fun onTemplateUpdated(oldTemplate: PromptTemplate, newTemplate: PromptTemplate) {
        SwingUtilities.invokeLater {
            loadData()
        }
    }
    
    override fun onTemplateDeleted(template: PromptTemplate) {
        SwingUtilities.invokeLater {
            loadData()
        }
    }
    
    override fun onTemplateShortcutChanged(template: PromptTemplate, oldShortcut: String?, newShortcut: String?) {
        SwingUtilities.invokeLater {
            loadData()
        }
    }
}