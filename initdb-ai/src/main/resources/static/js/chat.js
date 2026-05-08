/* global Api, normalizePagePayload, notifyErrorUnlessShown, showErrorDialog, escapeHtml, marked, openModalAnimated, closeModalAnimated */
'use strict';

let sessions = [];
let currentSessionId = null;
let sessionNameSuffixCounter = 1;

const DB_SESSIONS_STORAGE_KEY = 'dbSessions';

function readPersistedState() {
    try {
        const raw = localStorage.getItem(DB_SESSIONS_STORAGE_KEY);
        if (!raw) {
            return {sessions: [], currentSessionId: null};
        }
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
            return {sessions: parsed, currentSessionId: null};
        }
        return {
            sessions: Array.isArray(parsed.sessions) ? parsed.sessions : [],
            currentSessionId: parsed.currentSessionId || null
        };
    } catch (e) {
        console.warn('readPersistedState failed', e);
        return {sessions: [], currentSessionId: null};
    }
}

function saveSessions() {
    localStorage.setItem(
        DB_SESSIONS_STORAGE_KEY,
        JSON.stringify({sessions, currentSessionId})
    );
}

async function loadSessionsFromServer() {
    try {
        const serverSessions = await Api.get('/chat/query/list');
        const {sessions: persistedSessions, currentSessionId: persistedCurrentId} = readPersistedState();
        const messagesBySessionId = new Map(
            (persistedSessions || []).map(ps => [ps.id, Array.isArray(ps.messages) ? ps.messages : []])
        );

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
            messages: messagesBySessionId.get(s.sessionId) || []
        }));

        renderSessionList();

        if (sessions.length === 0) {
            currentSessionId = null;
            saveSessions();
            return;
        }

        const preferredId =
            persistedCurrentId && sessions.some(s => s.id === persistedCurrentId)
                ? persistedCurrentId
                : sessions[0].id;
        switchSession(preferredId);
    } catch (error) {
        console.error('Failed to load sessions:', error);
        notifyErrorUnlessShown(error, '加载会话列表失败');
    }
}

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
        notifyErrorUnlessShown(error, '删除会话失败');
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
    saveSessions();
}

async function loadChatDatasourceOptions() {
    const select = document.getElementById('chatDatasourceSelect');
    const hint = document.getElementById('chatDatasourceHint');
    select.innerHTML = '<option value="">加载中…</option>';
    select.disabled = true;
    if (hint) {
        hint.style.display = 'block';
    }
    try {
        const raw = await Api.get('/datasource/query/page', {
            pageNum: 1,
            pageSize: 200,
            status: 1,
            testStatus: 1
        });
        const pageData = normalizePagePayload(raw);
        const rows = pageData.records || [];
        select.innerHTML = '';
        if (!rows.length) {
            select.innerHTML = '<option value="">暂无可用的数据源</option>';
            return;
        }
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = '请选择数据源';
        select.appendChild(placeholder);
        rows.forEach(ds => {
            const opt = document.createElement('option');
            opt.value = ds.id;
            const type = ds.type || '数据库';
            const host = ds.host || '';
            const port = ds.port ? `:${ds.port}` : '';
            const db = ds.databaseName ? ` / ${ds.databaseName}` : '';
            opt.textContent = `${ds.name}（${type} · ${host}${port}${db}）`;
            select.appendChild(opt);
        });
        select.value = '';
    } catch (error) {
        console.error('Load chat datasource options:', error);
        select.innerHTML = '<option value="">加载失败</option>';
        notifyErrorUnlessShown(error, '加载数据源列表失败');
    } finally {
        select.disabled = false;
    }
}

async function createModal() {
    openModalAnimated(document.getElementById('sessionModal'));
    await loadChatDatasourceOptions();
}

async function deleteModal() {
    try {
        await Api.del('/chat/delete/all');
    } catch (error) {
        console.error('Delete all sessions error:', error);
        notifyErrorUnlessShown(error, '删除全部对话失败');
    }

    sessions = [];
    currentSessionId = null;
    localStorage.removeItem(DB_SESSIONS_STORAGE_KEY);
    renderSessionList();
    document.getElementById('chatContainer').style.display = 'none';
    document.getElementById('welcomeScreen').style.display = 'flex';
}

function closeModal() {
    closeModalAnimated(document.getElementById('sessionModal'));
    document.getElementById('sessionName').value = '新的对话' + (sessionNameSuffixCounter++);
    const select = document.getElementById('chatDatasourceSelect');
    if (select) {
        select.innerHTML = '<option value="">请选择数据源</option>';
        select.value = '';
    }
}

async function createNewSession() {
    const sessionName = document.getElementById('sessionName').value.trim();
    const datasourceId = (document.getElementById('chatDatasourceSelect').value || '').trim();

    if (!datasourceId) {
        showErrorDialog({title: '提示', message: '请选择数据源'});
        return;
    }

    try {
        const data = await Api.post('/chat/create', {
            sessionName,
            datasourceId
        });

        const newSessionId = data.sessionId;
        const newSession = {
            id: newSessionId,
            name: data.sessionName || sessionName,
            config: {
                host: data.host,
                port: data.port,
                url: data.url,
                username: data.username,
                password: data.password
            },
            messages: []
        };

        sessions.unshift(newSession);
        saveSessions();
        renderSessionList();
        switchSession(newSession.id);
        closeModal();
    } catch (error) {
        console.error('Connection error:', error);
        notifyErrorUnlessShown(error, '创建会话失败');
    }
}

function setSendButtonDisabled(disabled) {
    const btn = document.getElementById('sendMessageBtn');
    if (btn) btn.disabled = !!disabled;
}

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
    saveSessions();
}

function renderChatMessages(messages) {
    const chatMessagesElement = document.getElementById('chatMessages');
    chatMessagesElement.innerHTML = '';

    messages.forEach(msg => {
        addMessageToDOM(msg.role, msg.content);
    });

    chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
}

function parseBotMarkdown(md) {
    const raw = String(md ?? '');
    if (typeof marked !== 'undefined' && typeof marked.parse === 'function') {
        try {
            return marked.parse(raw, {breaks: true});
        } catch (e) {
            console.warn('Markdown parse failed', e);
        }
    }
    return `<pre class="bot-md-fallback">${escapeHtml(raw)}</pre>`;
}

function setBotMessageHtml(messageDiv, md) {
    messageDiv.innerHTML = `<div class="bot-message-body">${parseBotMarkdown(md)}</div>`;
}

/** 流式输出用纯文本，避免不完整 Markdown 反复 marked 导致重叠/错乱；结束后再 setBotMessageHtml */
function setBotMessageStreamingPlain(messageDiv, plain) {
    messageDiv.innerHTML =
        `<div class="bot-message-body"><pre class="bot-stream-plain">${escapeHtml(plain)}</pre></div>`;
}

function addMessageToDOM(role, content) {
    const chatMessagesElement = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    if (role === 'user') {
        messageDiv.innerHTML = escapeHtml(content);
    } else {
        setBotMessageHtml(messageDiv, content);
    }
    chatMessagesElement.appendChild(messageDiv);

    chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
    return messageDiv;
}

async function sendMessage() {
    const inputElement = document.getElementById('userInput');
    const message = inputElement.value.trim();

    if (!message) return;

    if (!currentSessionId) {
        showErrorDialog({title: '提示', message: '请先创建并选择一个对话'});
        return;
    }

    const sessionIndex = sessions.findIndex(s => s.id === currentSessionId);
    if (sessionIndex === -1) return;

    const userMsg = {role: 'user', content: message};
    sessions[sessionIndex].messages.push(userMsg);
    addMessageToDOM('user', message);
    inputElement.value = '';

    inputElement.disabled = true;
    setSendButtonDisabled(true);

    try {
        const response = await Api.streamPost('/db/chat/stream', {
            message,
            sessionId: currentSessionId
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status} ${response.statusText}${errorText ? ` - ${errorText}` : ''}`);
        }

        if (!response.body) {
            throw new Error('请求失败：未获取到流式响应体');
        }

        const botMessageDiv = addMessageToDOM('bot', '');
        const chatMessagesEl = document.getElementById('chatMessages');
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let fullResponse = '';
        let streamRafId = null;

        const flushStreamToDom = () => {
            streamRafId = null;
            setBotMessageStreamingPlain(botMessageDiv, fullResponse);
            chatMessagesEl.scrollTop = chatMessagesEl.scrollHeight;
        };

        const scheduleStreamFlush = () => {
            if (streamRafId === null) {
                streamRafId = requestAnimationFrame(flushStreamToDom);
            }
        };

        while (true) {
            const {done, value} = await reader.read();
            if (done) break;

            fullResponse += decoder.decode(value, {stream: true});
            scheduleStreamFlush();
        }

        fullResponse += decoder.decode();

        if (streamRafId !== null) {
            cancelAnimationFrame(streamRafId);
            streamRafId = null;
        }

        if (!fullResponse) {
            throw new Error('请求失败：返回内容为空');
        }

        setBotMessageHtml(botMessageDiv, fullResponse);
        chatMessagesEl.scrollTop = chatMessagesEl.scrollHeight;

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

    saveSessions();
}

function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

function triggerFileInput() {
    document.getElementById('fileInput').click();
}

function resetFileSelectionUI() {
    document.getElementById('fileInput').value = '';
    window.selectedFile = null;
    document.getElementById('fileList').style.display = 'none';
    document.getElementById('fileNameText').textContent = '';

    const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
    uploadFileSendBtn.classList.add('btn-send-file--hidden');
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

    if (files.length > 1) {
        showErrorDialog({title: '提示', message: '每次只能上传一个文件，请重新选择。'});
        resetFileSelectionUI();
        return;
    }

    const file = fileInput.files[0];
    if (!file) {
        resetFileSelectionUI();
        return;
    }

    if (file.type !== 'text/plain') {
        showErrorDialog({title: '提示', message: '不支持的文件类型。请上传 .txt 文件。'});
        resetFileSelectionUI();
        return;
    }

    window.selectedFile = file;
    document.getElementById('fileNameText').textContent = file.name;
    document.getElementById('fileList').style.display = 'block';

    const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
    uploadFileSendBtn.classList.remove('btn-send-file--hidden');
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
    if (userInput.disabled) {
        showErrorDialog({title: '提示', message: '请先开启一个会话后再上传文件。'});
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
        notifyErrorUnlessShown(error, '文件上传失败');
    } finally {
        resetFileSelectionUI();
    }
}
