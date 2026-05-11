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

function readSessionIdFromUrl() {
    try {
        const u = new URL(window.location.href);
        return (u.searchParams.get('session') || '').trim();
    } catch (_) {
        return '';
    }
}

function stripSessionQueryFromUrl() {
    try {
        const u = new URL(window.location.href);
        if (!u.searchParams.has('session')) {
            return;
        }
        u.searchParams.delete('session');
        const qs = u.searchParams.toString();
        history.replaceState(null, '', u.pathname + (qs ? '?' + qs : '') + u.hash);
    } catch (_) {
        /* ignore */
    }
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
            stripSessionQueryFromUrl();
            return;
        }

        const urlSessionId = readSessionIdFromUrl();
        const preferredId =
            urlSessionId && sessions.some(s => s.id === urlSessionId)
                ? urlSessionId
                : persistedCurrentId && sessions.some(s => s.id === persistedCurrentId)
                    ? persistedCurrentId
                    : sessions[0].id;
        switchSession(preferredId);
        if (urlSessionId) {
            stripSessionQueryFromUrl();
        }
    } catch (error) {
        console.error('Failed to load sessions:', error);
        notifyErrorUnlessShown(error, '加载会话列表失败');
    }
}

const SESSION_NAME_PULL_DELAY_MS = 2000;
const SESSION_NAME_PULL_MAX_ATTEMPTS = 3;

function delayMs(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * 仅根据服务端列表更新本地 {@link sessions} 的展示名称（不改 messages / config / 当前选中会话）。
 * 供异步会话重命名后侧边栏刷新；失败由调用方捕获。
 */
async function refreshSessionNamesFromServer() {
    const serverSessions = await Api.get('/chat/query/list');
    if (!Array.isArray(serverSessions)) {
        return;
    }
    const nameById = new Map(
        serverSessions.map(s => {
            const id = s.sessionId;
            const name = s.sessionName != null ? String(s.sessionName) : '';
            return [id, name];
        })
    );
    let anyChanged = false;
    for (const local of sessions) {
        if (!nameById.has(local.id)) {
            continue;
        }
        const nextName = nameById.get(local.id);
        const prevName = local.name != null ? String(local.name) : '';
        if (nextName !== prevName) {
            local.name = nextName;
            anyChanged = true;
        }
    }
    if (anyChanged) {
        renderSessionList();
        saveSessions();
    }
}

/**
 * 流结束后延迟拉取会话名：每隔 {@link SESSION_NAME_PULL_DELAY_MS} 尝试一次，最多 {@link SESSION_NAME_PULL_MAX_ATTEMPTS} 次。
 * 不 await，避免阻塞发送按钮恢复。
 */
function scheduleSessionNamePullAfterChatStream() {
    void (async () => {
        for (let attempt = 0; attempt < SESSION_NAME_PULL_MAX_ATTEMPTS; attempt++) {
            await delayMs(SESSION_NAME_PULL_DELAY_MS);
            try {
                await refreshSessionNamesFromServer();
            } catch (e) {
                console.warn('refreshSessionNamesFromServer failed', e);
            }
        }
    })();
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
        resetContextualizePreview();
    }

    renderSessionList();
    saveSessions();
}

function resetContextualizePreview() {
    const wrap = document.getElementById('standalonePreviewWrap');
    const ta = document.getElementById('standalonePreviewInput');
    if (ta) {
        ta.value = '';
    }
    if (wrap) {
        wrap.style.display = 'none';
    }
}

function applyStandaloneToUserInput() {
    const ta = document.getElementById('standalonePreviewInput');
    const input = document.getElementById('userInput');
    if (!ta || !input) {
        return;
    }
    const text = ta.value.trim();
    if (!text) {
        showErrorDialog({title: '提示', message: '补全内容为空'});
        return;
    }
    input.value = text;
    resetContextualizePreview();
    input.focus();
}

async function previewContextualize() {
    const input = document.getElementById('userInput');
    const message = input.value.trim();
    if (!message) {
        showErrorDialog({title: '提示', message: '请先输入问题'});
        return;
    }
    if (!currentSessionId) {
        showErrorDialog({title: '提示', message: '请先创建并选择一个对话'});
        return;
    }
    const btn = document.getElementById('contextualizeBtn');
    if (btn) {
        btn.disabled = true;
    }
    try {
        // ContextualizeController：ResponseResult<String>，Api.post 解包后为补全后的字符串
        const data = await Api.post('/db/chat/contextualize', {message, sessionId: currentSessionId});
        const rewrite =
            typeof data === 'string' ? data : data && typeof data.rewrite === 'string' ? data.rewrite : '';
        const wrap = document.getElementById('standalonePreviewWrap');
        const ta = document.getElementById('standalonePreviewInput');
        if (ta) {
            ta.value = rewrite;
        }
        if (wrap) {
            wrap.style.display = 'flex';
        }
    } catch (error) {
        console.error('previewContextualize:', error);
        notifyErrorUnlessShown(error, '补全会话失败');
    } finally {
        const inputEl = document.getElementById('userInput');
        if (btn) {
            btn.disabled = !!(inputEl && inputEl.disabled);
        }
    }
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
    resetContextualizePreview();
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
    const ctxBtn = document.getElementById('contextualizeBtn');
    if (ctxBtn) ctxBtn.disabled = !!disabled;
    const fillBtn = document.getElementById('standaloneFillBtn');
    if (fillBtn) fillBtn.disabled = !!disabled;
}

function switchSession(sessionId) {
    resetContextualizePreview();
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
        if (msg.role === 'bot' && (msg.contextualize || msg.trace)) {
            addMessageToDOM('bot', {
                contextualize: msg.contextualize,
                trace: msg.trace,
                content: msg.content || ''
            });
        } else {
            addMessageToDOM(msg.role, msg.content);
        }
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

/** 补全区块 + 思考过程（工作流 trace）+ 助手正文（最终态，answer 走 Markdown） */
function setBotMessageStructured(messageDiv, contextualizeText, traceText, answerMd) {
    const ctx = String(contextualizeText ?? '').trim();
    const ctxBlock = ctx
        ? `<div class="bot-contextualize-wrap" role="note" aria-label="补全的会话">
        <div class="bot-contextualize-label">补全的会话</div>
        <div class="bot-contextualize-body">${escapeHtml(ctx)}</div>
    </div>`
        : '';
    const tr = String(traceText ?? '').trim();
    const traceBlock = tr
        ? `<div class="bot-trace-wrap" role="note" aria-label="思考过程">
        <div class="bot-trace-label">思考过程</div>
        <div class="bot-trace-body">${escapeHtml(tr)}</div>
    </div>`
        : '';
    const answerLead = tr ? '<div class="bot-answer-label">正式回答</div>' : '';
    const body = answerMd ? parseBotMarkdown(answerMd) : '';
    messageDiv.innerHTML = `${ctxBlock}${traceBlock}<div class="bot-message-body">${answerLead}${body}</div>`;
}

/** 流式：补全 / 思考过程 / 正式回答 三区；思考与回答流式阶段为纯文本，结束后再 setBotMessageStructured */
function setBotMessageStreamingStructured(messageDiv, contextualizeText, tracePlain, answerPlain) {
    const ctx = String(contextualizeText ?? '').trim();
    const ctxBlock = ctx
        ? `<div class="bot-contextualize-wrap" role="note" aria-label="补全的会话">
        <div class="bot-contextualize-label">补全的会话</div>
        <div class="bot-contextualize-body">${escapeHtml(ctx)}</div>
    </div>`
        : '';
    const tr = String(tracePlain ?? '').trim();
    const traceBlock = tr
        ? `<div class="bot-trace-wrap" role="note" aria-label="思考过程">
        <div class="bot-trace-label">思考过程</div>
        <div class="bot-trace-body"><pre class="bot-stream-plain bot-trace-pre">${escapeHtml(tr)}</pre></div>
    </div>`
        : '';
    const answerLead = tr ? '<div class="bot-answer-label">正式回答</div>' : '';
    messageDiv.innerHTML = `${ctxBlock}${traceBlock}<div class="bot-message-body">${answerLead}<pre class="bot-stream-plain">${escapeHtml(
        String(answerPlain ?? '')
    )}</pre></div>`;
}

/**
 * @param {string} role
 * @param {string|{contextualize?: string, content: string}} content 用户侧为字符串；助手可为旧版纯字符串或 { contextualize, content }
 */
function addMessageToDOM(role, content) {
    const chatMessagesElement = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    if (role === 'user') {
        messageDiv.innerHTML = escapeHtml(content);
    } else if (content && typeof content === 'object') {
        setBotMessageStructured(messageDiv, content.contextualize, content.trace, content.content || '');
    } else {
        setBotMessageHtml(messageDiv, content || '');
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
        let lineBuffer = '';
        let contextualizeText = '';
        let traceAccum = '';
        let answerAccum = '';
        let streamRafId = null;

        const consumeNdjsonLines = chunkStr => {
            lineBuffer += chunkStr;
            let nl;
            while ((nl = lineBuffer.indexOf('\n')) >= 0) {
                const line = lineBuffer.slice(0, nl).trim();
                lineBuffer = lineBuffer.slice(nl + 1);
                if (!line) {
                    continue;
                }
                try {
                    const o = JSON.parse(line);
                    if (o.p === 'contextualize' && typeof o.t === 'string') {
                        contextualizeText = o.t;
                    } else if (o.p === 'trace' && typeof o.t === 'string') {
                        traceAccum += (traceAccum ? '\n' : '') + o.t;
                    } else if (o.p === 'answer' && typeof o.t === 'string') {
                        answerAccum += o.t;
                    }
                } catch (e) {
                    console.warn('NDJSON stream line parse failed', e, line);
                }
            }
        };

        const flushStreamToDom = () => {
            streamRafId = null;
            setBotMessageStreamingStructured(botMessageDiv, contextualizeText, traceAccum, answerAccum);
            chatMessagesEl.scrollTop = chatMessagesEl.scrollHeight;
        };

        const scheduleStreamFlush = () => {
            if (streamRafId === null) {
                streamRafId = requestAnimationFrame(flushStreamToDom);
            }
        };

        while (true) {
            const {done, value} = await reader.read();
            if (done) {
                break;
            }

            consumeNdjsonLines(decoder.decode(value, {stream: true}));
            scheduleStreamFlush();
        }

        consumeNdjsonLines(decoder.decode());

        if (streamRafId !== null) {
            cancelAnimationFrame(streamRafId);
            streamRafId = null;
        }

        if (!contextualizeText && !answerAccum && !traceAccum) {
            throw new Error('请求失败：返回内容为空');
        }

        setBotMessageStructured(botMessageDiv, contextualizeText, traceAccum, answerAccum);
        chatMessagesEl.scrollTop = chatMessagesEl.scrollHeight;

        let botMsg;
        if (contextualizeText || traceAccum) {
            botMsg = {role: 'bot', content: answerAccum};
            if (contextualizeText) {
                botMsg.contextualize = contextualizeText;
            }
            if (traceAccum) {
                botMsg.trace = traceAccum;
            }
        } else {
            botMsg = {role: 'bot', content: answerAccum};
        }
        sessions[sessionIndex].messages.push(botMsg);
        resetContextualizePreview();
        scheduleSessionNamePullAfterChatStream();
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

function handleUserInputKeydown(event) {
    if (event.key !== 'Enter') {
        return;
    }
    if (event.ctrlKey || event.metaKey) {
        event.preventDefault();
        if (isStandalonePreviewVisible()) {
            const ta = document.getElementById('standalonePreviewInput');
            if (ta && ta.value.trim()) {
                applyStandaloneToUserInput();
            }
        }
        return;
    }
    event.preventDefault();
    sendMessage();
}

function isStandalonePreviewVisible() {
    const wrap = document.getElementById('standalonePreviewWrap');
    if (!wrap) {
        return false;
    }
    const d = wrap.style.display;
    return d === 'flex' || d === 'block';
}

function handleChatContainerKeydown(event) {
    if (event.key === 'F2') {
        const ctxBtn = document.getElementById('contextualizeBtn');
        if (ctxBtn && !ctxBtn.disabled && currentSessionId) {
            event.preventDefault();
            void previewContextualize();
        }
        return;
    }

    const fillCombo = (event.ctrlKey || event.metaKey) && event.key === 'Enter';
    if (!fillCombo) {
        return;
    }
    if (!isStandalonePreviewVisible()) {
        return;
    }
    const ta = document.getElementById('standalonePreviewInput');
    if (!ta || !ta.value.trim()) {
        return;
    }
    const tag = (event.target && event.target.tagName) || '';
    if (tag === 'TEXTAREA') {
        event.preventDefault();
        applyStandaloneToUserInput();
    }
}

function initChatKeyboardShortcuts() {
    const chatContainer = document.getElementById('chatContainer');
    if (chatContainer) {
        chatContainer.addEventListener('keydown', handleChatContainerKeydown);
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initChatKeyboardShortcuts);
} else {
    initChatKeyboardShortcuts();
}
