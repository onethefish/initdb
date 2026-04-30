// 页面交互逻辑（从 index.html 内联脚本迁移而来）
/* global Api */

const Api = window.Api;

// 用于存储所有会话的数组
// 初始化会话不再依赖本地缓存：改为从后端接口拉取
let sessions = [];
let currentSessionId = null;
let activeMainTab = 'datasource';
let datasourceList = [];
let datasourcePageNum = 1;
let datasourcePageSize = 10;
let datasourceTotal = 0;
let editingDatasourceId = null;
let selectedDatasourceIds = new Set();
const MAIN_TAB_STORAGE_KEY = 'activeMainTab';

function saveSessions() {
    localStorage.setItem('dbSessions', JSON.stringify(sessions));
}

// 从后端拉取会话列表并初始化页面
async function loadSessionsFromServer() {
    try {
        const serverSessions = await Api.get('/chat/query/list');

        // 适配后端字段：{ sessionId, sessionName, username, password, url, ... }
        sessions = (serverSessions || []).map(s => ({
            id: s.sessionId,
            name: s.sessionName,
            config: {
                host: s.host,
                port: s.port,
                url: s.url,
                username: s.username,
                password: s.password
            },
            messages: []
        }));

        renderSessionList();

        // 如果有会话，默认加载第一个
        if (sessions.length > 0 && !currentSessionId) {
            switchSession(sessions[0].id);
        }
    } catch (error) {
        console.error('Failed to load sessions:', error);
        // 保持欢迎界面显示（不阻断页面功能）
    }
}

function switchMainTab(tab) {
    activeMainTab = tab === 'chat' ? 'chat' : 'datasource';
    localStorage.setItem(MAIN_TAB_STORAGE_KEY, activeMainTab);
    const datasourceTabBtn = document.getElementById('datasourceTabBtn');
    const chatTabBtn = document.getElementById('chatTabBtn');
    const datasourcePage = document.getElementById('datasourcePage');
    const chatPage = document.getElementById('chatPage');

    if (activeMainTab === 'datasource') {
        datasourceTabBtn.classList.add('active');
        chatTabBtn.classList.remove('active');
        datasourcePage.classList.add('page-panel-active');
        chatPage.classList.remove('page-panel-active');
        queryDatasourcePage();
    } else {
        chatTabBtn.classList.add('active');
        datasourceTabBtn.classList.remove('active');
        chatPage.classList.add('page-panel-active');
        datasourcePage.classList.remove('page-panel-active');
    }
}

function getSavedMainTab() {
    const savedTab = localStorage.getItem(MAIN_TAB_STORAGE_KEY);
    return savedTab === 'chat' ? 'chat' : 'datasource';
}

function getDatasourceFilters() {
    return {
        name: document.getElementById('dsFilterName').value.trim(),
        type: document.getElementById('dsFilterType').value.trim(),
        host: document.getElementById('dsFilterHost').value.trim(),
        status: document.getElementById('dsFilterStatus').value
    };
}

function mapStatusTag(status) {
    return Number(status) === 1
        ? '<span class="status-tag status-enabled">启用</span>'
        : '<span class="status-tag status-disabled">禁用</span>';
}

function mapTestStatusTag(testStatus) {
    if (Number(testStatus) === 1) return '<span class="status-tag test-success">成功</span>';
    if (Number(testStatus) === 0) return '<span class="status-tag test-failed">失败</span>';
    return '<span class="status-tag test-unknown">未测</span>';
}

function escapeHtml(text) {
    return String(text || '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function updateDatasourcePageInfo() {
    const totalPages = datasourceTotal > 0 ? Math.ceil(datasourceTotal / datasourcePageSize) : 1;
    document.getElementById('datasourcePageInfo').textContent = `第 ${datasourcePageNum} 页 / 共 ${totalPages} 页（${datasourceTotal} 条）`;
}

function renderDatasourceTable() {
    const tbody = document.getElementById('datasourceTableBody');
    tbody.innerHTML = '';
    selectedDatasourceIds.clear();
    document.getElementById('selectAllDatasource').checked = false;

    if (!datasourceList.length) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td colspan="10" style="text-align:center;color:#999;">暂无数据源，请点击“新增数据源”创建</td>';
        tbody.appendChild(tr);
        updateDatasourcePageInfo();
        return;
    }

    datasourceList.forEach(ds => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><input data-id="${ds.id}" type="checkbox" onchange="toggleDatasourceSelection('${ds.id}', this.checked)"></td>
            <td>${escapeHtml(ds.name)}</td>
            <td>${escapeHtml(ds.type)}</td>
            <td>${escapeHtml(ds.host)}:${escapeHtml(ds.port)}</td>
            <td>${escapeHtml(ds.databaseName)}</td>
            <td>${escapeHtml(ds.username)}</td>
            <td>${mapStatusTag(ds.status)}</td>
            <td>${mapTestStatusTag(ds.testStatus)}</td>
            <td>${escapeHtml(ds.description)}</td>
            <td>
                <div class="table-actions">
                  <button type="button" onclick="testDatasourceRow('${ds.id}')">测试</button>
                  <button type="button" onclick="openDatasourceModal('${ds.id}')">编辑</button>
                  <button type="button" onclick="deleteDatasource('${ds.id}')">删除</button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
    updateDatasourcePageInfo();
}

async function queryDatasourcePage() {
    try {
        const params = {
            ...getDatasourceFilters(),
            pageNum: datasourcePageNum,
            pageSize: datasourcePageSize
        };
        const pageData = await Api.get('/datasource/query/page', params);
        datasourceList = pageData.records || [];
        datasourceTotal = Number(pageData.total || 0);
        datasourcePageNum = Number(pageData.current || datasourcePageNum);
        datasourcePageSize = Number(pageData.size || datasourcePageSize);
        renderDatasourceTable();
    } catch (error) {
        console.error('Query datasource page error:', error);
        alert(error.message || '查询数据源失败');
    }
}

function resetDatasourceFilters() {
    document.getElementById('dsFilterName').value = '';
    document.getElementById('dsFilterType').value = '';
    document.getElementById('dsFilterHost').value = '';
    document.getElementById('dsFilterStatus').value = '';
    datasourcePageNum = 1;
    queryDatasourcePage();
}

function changeDatasourcePage(direction) {
    const totalPages = datasourceTotal > 0 ? Math.ceil(datasourceTotal / datasourcePageSize) : 1;
    const next = datasourcePageNum + direction;
    if (next < 1 || next > totalPages) return;
    datasourcePageNum = next;
    queryDatasourcePage();
}

function toggleDatasourceSelection(id, checked) {
    if (checked) selectedDatasourceIds.add(id);
    else selectedDatasourceIds.delete(id);
}

function toggleSelectAllDatasource(checkbox) {
    const checked = !!checkbox.checked;
    const list = document.querySelectorAll('#datasourceTableBody input[type="checkbox"]');
    list.forEach(item => {
        item.checked = checked;
        const id = item.dataset.id;
        if (!id) return;
        if (checked) selectedDatasourceIds.add(id);
        else selectedDatasourceIds.delete(id);
    });
}

function fillDatasourceForm(ds) {
    document.getElementById('dsName').value = ds?.name || '';
    document.getElementById('dsType').value = ds?.type || '';
    document.getElementById('dsHost').value = ds?.host || '';
    document.getElementById('dsPort').value = ds?.port || '';
    document.getElementById('dsDatabaseName').value = ds?.databaseName || '';
    document.getElementById('dsUsername').value = ds?.username || '';
    document.getElementById('dsPassword').value = ds?.password || '';
    document.getElementById('dsConnectionUrl').value = ds?.connectionUrl || '';
    document.getElementById('dsStatus').value = String(ds?.status ?? 1);
    document.getElementById('dsDescription').value = ds?.description || '';
}

function collectDatasourceForm() {
    return {
        id: editingDatasourceId || undefined,
        name: document.getElementById('dsName').value.trim(),
        type: document.getElementById('dsType').value.trim(),
        host: document.getElementById('dsHost').value.trim(),
        port: document.getElementById('dsPort').value.trim(),
        databaseName: document.getElementById('dsDatabaseName').value.trim(),
        username: document.getElementById('dsUsername').value.trim(),
        password: document.getElementById('dsPassword').value,
        connectionUrl: document.getElementById('dsConnectionUrl').value.trim(),
        status: Number(document.getElementById('dsStatus').value),
        description: document.getElementById('dsDescription').value.trim()
    };
}

async function openDatasourceModal(id) {
    editingDatasourceId = id || null;
    document.getElementById('datasourceModalTitle').textContent = editingDatasourceId ? '编辑数据源' : '新增数据源';

    if (!editingDatasourceId) {
        fillDatasourceForm(null);
        document.getElementById('datasourceModal').style.display = 'flex';
        return;
    }

    try {
        const ds = await Api.get('/datasource/query/unique', {id: editingDatasourceId});
        fillDatasourceForm(ds || {});
        document.getElementById('datasourceModal').style.display = 'flex';
    } catch (error) {
        console.error('Query datasource unique error:', error);
        alert(error.message || '加载数据源详情失败');
    }
}

function closeDatasourceModal() {
    editingDatasourceId = null;
    document.getElementById('datasourceModal').style.display = 'none';
}

async function submitDatasourceForm() {
    const body = collectDatasourceForm();
    if (!body.name || !body.type || !body.host || !body.port) {
        alert('名称、类型、主机、端口为必填项');
        return;
    }
    try {
        if (editingDatasourceId) await Api.put('/datasource/update', body);
        else await Api.post('/datasource/add', body);
        closeDatasourceModal();
        await queryDatasourcePage();
    } catch (error) {
        console.error('Save datasource error:', error);
        alert(error.message || '保存数据源失败');
    }
}

async function testDatasourceConnection() {
    const body = collectDatasourceForm();
    if (!body.host || !body.port) {
        alert('请先填写主机和端口');
        return;
    }
    try {
        await Api.post('/datasource/test', body);
        alert('连接测试成功');
        await queryDatasourcePage();
    } catch (error) {
        console.error('Test datasource connection error:', error);
        alert(error.message || '连接测试失败');
    }
}

async function testDatasourceRow(id) {
    try {
        const ds = await Api.get('/datasource/query/unique', {id});
        await Api.post('/datasource/test', ds);
        alert('连接测试成功');
        await queryDatasourcePage();
    } catch (error) {
        console.error('Test datasource row error:', error);
        alert(error.message || '连接测试失败');
    }
}

async function deleteDatasource(id) {
    if (!confirm('确认删除该数据源吗？')) return;
    try {
        await Api.del('/datasource/delete', {body: {id}});
        await queryDatasourcePage();
    } catch (error) {
        console.error('Delete datasource error:', error);
        alert(error.message || '删除数据源失败');
    }
}

async function deleteSelectedDatasource() {
    if (!selectedDatasourceIds.size) {
        alert('请先选择要删除的数据源');
        return;
    }
    if (!confirm(`确认删除选中的 ${selectedDatasourceIds.size} 条数据源吗？`)) return;
    try {
        const list = Array.from(selectedDatasourceIds).map(id => ({id}));
        await Api.del('/datasource/delete/batch', {body: list});
        selectedDatasourceIds.clear();
        await queryDatasourcePage();
    } catch (error) {
        console.error('Delete datasource batch error:', error);
        alert(error.message || '批量删除失败');
    }
}

// 渲染会话列表
function renderSessionList() {
    const sessionListElement = document.getElementById('sessionList');
    sessionListElement.innerHTML = '';

    sessions.forEach(session => {
        const div = document.createElement('div');
        div.className = `session-item ${session.id === currentSessionId ? 'active' : ''}`;
        div.onclick = () => switchSession(session.id);

        const nameSpan = document.createElement('span');
        nameSpan.className = 'session-name';
        nameSpan.textContent = session.name;

        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'session-delete-btn';
        deleteBtn.type = 'button';
        deleteBtn.textContent = '🗑';
        deleteBtn.title = '删除当前会话';
        deleteBtn.onclick = async (event) => {
            event.stopPropagation();
            await deleteSession(session.id);
        };

        div.appendChild(nameSpan);
        div.appendChild(deleteBtn);
        sessionListElement.appendChild(div);
    });
}

async function deleteSession(sessionId) {
    if (!sessionId) return;

    try {
        await Api.del('/chat/delete', {
            body: {sessionId}
        });
    } catch (error) {
        console.error('Delete session error:', error);
        alert(error.message || '删除会话失败');
        return;
    }

    const deletedIndex = sessions.findIndex(s => s.id === sessionId);
    if (deletedIndex === -1) return;

    sessions.splice(deletedIndex, 1);

    if (currentSessionId === sessionId) {
        currentSessionId = null;
        const nextSession = sessions[deletedIndex] || sessions[deletedIndex - 1];
        if (nextSession) {
            switchSession(nextSession.id);
            return;
        }

        document.getElementById('chatContainer').style.display = 'none';
        document.getElementById('welcomeScreen').style.display = 'flex';
    }

    renderSessionList();
}

// 打开模态框
function createModal() {
    document.getElementById('sessionModal').style.display = 'flex';
}

async function deleteModal() {
    try {
        await Api.del('/chat/delete/all');
    } catch (error) {
        console.log(error);
    }

    sessions = [];
    // 只清理本应用的会话缓存
    localStorage.removeItem('dbSessions');
    renderSessionList();
    document.getElementById('chatContainer').style.display = 'none';
    document.getElementById('welcomeScreen').style.display = 'flex';
}

let index = 1;

// 关闭创建新会话框
function closeModal() {
    document.getElementById('sessionModal').style.display = 'none';

    // 清空表单并重置为默认值
    document.getElementById('sessionName').value = '新的对话' + (index++);
    document.getElementById('url').value = 'jdbc:h2:C:\\Users\\57172\\github\\initdb\\initdb-ai\\db\\initdb';
    document.getElementById('username').value = '';
    document.getElementById('password').value = '';
}

// 创建新会话
async function createNewSession() {
    const sessionName = document.getElementById('sessionName').value.trim();
    const url = document.getElementById('url').value.trim();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    try {
        const data = await Api.post('/chat/create', {
            sessionName,
            url,
            username,
            password
        });

        const newSessionId = data.sessionId;
        const newSession = {
            id: newSessionId,
            name: sessionName,
            config: {username, password},
            messages: []
        };

        sessions.unshift(newSession);
        saveSessions();
        renderSessionList();
        switchSession(newSession.id);
        closeModal();
    } catch (error) {
        console.error('Connection error:', error);
        alert(error.message || '创建会话时候发送未知错误！');
    } finally {
        // todo
    }
}

function setSendButtonDisabled(disabled) {
    const btn = document.getElementById('sendMessageBtn');
    if (btn) btn.disabled = !!disabled;
}

// 切换会话
function switchSession(sessionId) {
    currentSessionId = sessionId;
    const session = sessions.find(s => s.id === sessionId);

    if (session) {
        document.getElementById('chatContainer').style.display = 'flex';
        document.getElementById('welcomeScreen').style.display = 'none';
        document.getElementById('currentSessionHeader').textContent = session.name;
        renderChatMessages(session.messages);

        document.getElementById('userInput').disabled = false;
        setSendButtonDisabled(false);
    }

    renderSessionList();
}

// 渲染特定会话的消息
function renderChatMessages(messages) {
    const chatMessagesElement = document.getElementById('chatMessages');
    chatMessagesElement.innerHTML = '';

    messages.forEach(msg => {
        addMessageToDOM(msg.role, msg.content);
    });

    // 滚动到底部
    chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
}

// 将消息添加到DOM
function addMessageToDOM(role, content) {
    const chatMessagesElement = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    messageDiv.innerHTML = content;
    chatMessagesElement.appendChild(messageDiv);

    chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
    return messageDiv;
}

// 发送消息
async function sendMessage() {
    const inputElement = document.getElementById('userInput');
    const message = inputElement.value.trim();

    if (!message) return;

    if (!currentSessionId) {
        alert('请先创建并选择一个对话');
        return;
    }

    const sessionIndex = sessions.findIndex(s => s.id === currentSessionId);
    if (sessionIndex === -1) return;

    // 添加用户消息
    const userMsg = {role: 'user', content: message};
    sessions[sessionIndex].messages.push(userMsg);
    addMessageToDOM('user', message);
    inputElement.value = '';

    // 禁用输入以等待响应
    inputElement.disabled = true;
    setSendButtonDisabled(true);

    try {
        const response = await fetch('/db/chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                message,
                sessionId: currentSessionId,
                db_config: sessions[sessionIndex].config
            })
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status} ${response.statusText}${errorText ? ` - ${errorText}` : ''}`);
        }

        if (!response.body) {
            throw new Error('请求失败：未获取到流式响应体');
        }

        const botMessageDiv = addMessageToDOM('bot', '');
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let fullResponse = '';

        while (true) {
            const {done, value} = await reader.read();
            if (done) break;

            // 后端会做 delta 处理，这里按增量进行累加显示
            fullResponse += decoder.decode(value, {stream: true});
            botMessageDiv.innerHTML = fullResponse;
            document.getElementById('chatMessages').scrollTop = document.getElementById('chatMessages').scrollHeight;
        }

        // 结束时 flush 一次（可能为空，保持兼容）
        fullResponse += decoder.decode();

        if (!fullResponse) {
            throw new Error('请求失败：返回内容为空');
        }

        const botMsg = {role: 'bot', content: fullResponse};
        sessions[sessionIndex].messages.push(botMsg);
    } catch (error) {
        console.error('Error:', error);
        const errorMsg = {role: 'bot', content: `错误: ${error.message}`};
        sessions[sessionIndex].messages.push(errorMsg);
        addMessageToDOM('bot', `错误: ${error.message}`);
    } finally {
        inputElement.disabled = false;
        setSendButtonDisabled(false);
        inputElement.focus();
    }

    // 发送完消息后保存会话
    saveSessions();
}

// 回车键发送消息
function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

// 页面加载时初始化
window.onload = async function () {
    switchMainTab(getSavedMainTab());
    // sessions 在 loadSessionsFromServer 内部填充
    await loadSessionsFromServer();
    // 否则保持欢迎界面显示
};

// 上传文件
function triggerFileInput() {
    document.getElementById('fileInput').click();
}

function resetFileSelectionUI() {
    document.getElementById('fileInput').value = '';
    window.selectedFile = null;
    document.getElementById('fileList').style.display = 'none';
    document.getElementById('fileNameText').textContent = '';

    const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
    uploadFileSendBtn.style.display = 'none';
    uploadFileSendBtn.disabled = true;
    uploadFileSendBtn.classList.remove('uploading');
    uploadFileSendBtn.setAttribute('aria-label', '上传并处理');
}

function handleFileSelect(fileInput) {
    const files = fileInput.files;
    if (files.length === 0) {
        resetFileSelectionUI();
        return;
    }

    // 严格限制为单个文件
    if (files.length > 1) {
        alert('每次只能上传一个文件，请重新选择。');
        resetFileSelectionUI();
        return;
    }

    const file = fileInput.files[0];
    if (!file) {
        resetFileSelectionUI();
        return;
    }

    // 检查文件类型是否为 .txt
    if (file.type !== 'text/plain') {
        alert('不支持的文件类型。请上传 .txt 文件。');
        resetFileSelectionUI();
        return;
    }

    window.selectedFile = file;
    document.getElementById('fileNameText').textContent = file.name;
    document.getElementById('fileList').style.display = 'block';

    const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
    uploadFileSendBtn.style.display = 'inline-flex';
    uploadFileSendBtn.disabled = false;
}

async function uploadSelectedFile() {
    if (!window.selectedFile) {
        console.warn('没有选中的文件可供上传。');
        return;
    }

    const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
    uploadFileSendBtn.disabled = true;
    uploadFileSendBtn.classList.add('uploading');
    uploadFileSendBtn.setAttribute('aria-label', '上传中...');

    const userInput = document.getElementById('userInput');
    // 检查输入框是否禁用，如果禁用则无法获取当前会话ID
    if (userInput.disabled) {
        alert('请先开启一个会话后再上传文件。');
        uploadFileSendBtn.disabled = false;
        uploadFileSendBtn.classList.remove('uploading');
        uploadFileSendBtn.setAttribute('aria-label', '上传并处理');
        return;
    }

    const formData = new FormData();
    formData.append('file', window.selectedFile);
    formData.append('sessionId', currentSessionId);

    try {
        const result = await Api.requestForm('/document/upload/txt', formData);
        console.log('文件上传成功:', result);
    } catch (error) {
        console.error('文件上传失败:', error);
    } finally {
        resetFileSelectionUI();
    }
}

