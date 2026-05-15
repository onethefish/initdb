/* global Api, normalizePagePayload, notifyErrorUnlessShown, showErrorDialog, escapeHtml, marked, openModalAnimated, closeModalAnimated */
'use strict';

let sessions = [];
let currentSessionId = null;
let sessionNameSuffixCounter = 1;

/** 智能对话流式输出进行中：仅禁用发送相关按钮，输入框可继续编辑下一条 */
let isDbChatStreaming = false;

/** 右侧「数据库表」列表：原始数据与并发加载序号 */
let dbTableListRaw = [];
let dbTableLoadSeq = 0;

function getDbTableUiEls() {
    return {
        placeholder: document.getElementById('dbTablePlaceholder'),
        loading: document.getElementById('dbTableLoading'),
        empty: document.getElementById('dbTableEmpty'),
        list: document.getElementById('dbTableList'),
        search: document.getElementById('dbTableSearchInput'),
        refreshBtn: document.getElementById('dbTableRefreshBtn')
    };
}

function syncDbTableSearchAndRefreshDisabled() {
    const hasSession = !!currentSessionId && sessions.some(s => s.id === currentSessionId);
    const {search, refreshBtn} = getDbTableUiEls();
    if (search) {
        search.disabled = !hasSession;
    }
    if (refreshBtn) {
        refreshBtn.disabled = !hasSession;
    }
}

function resetDbTablePanelToPlaceholder() {
    dbTableLoadSeq++;
    dbTableListRaw = [];
    const els = getDbTableUiEls();
    if (els.search) {
        els.search.value = '';
    }
    if (els.placeholder) {
        els.placeholder.hidden = false;
        els.placeholder.textContent = '请先创建并选择对话后显示表列表';
    }
    if (els.loading) {
        els.loading.hidden = true;
    }
    if (els.empty) {
        els.empty.hidden = true;
    }
    if (els.list) {
        els.list.innerHTML = '';
        els.list.hidden = true;
    }
    syncDbTableSearchAndRefreshDisabled();
}

function getDbTableFilterQuery() {
    const input = document.getElementById('dbTableSearchInput');
    return (input && String(input.value).trim().toLowerCase()) || '';
}

function filterDbTableList() {
    const q = getDbTableFilterQuery();
    let filtered = dbTableListRaw;
    if (q) {
        filtered = dbTableListRaw.filter(t => {
            const name = String(t.tableName || '').toLowerCase();
            const rem = String(t.remarks || '').toLowerCase();
            return name.includes(q) || rem.includes(q);
        });
    }
    renderDbTableRows(filtered);
}

function renderDbTableRows(filtered) {
    const els = getDbTableUiEls();
    if (!els.list || !els.empty) {
        return;
    }

    if (!dbTableListRaw.length) {
        els.list.hidden = true;
        els.list.innerHTML = '';
        els.empty.hidden = false;
        els.empty.textContent = '当前库中未找到用户表';
        return;
    }

    if (!filtered.length) {
        els.list.hidden = true;
        els.list.innerHTML = '';
        els.empty.hidden = false;
        els.empty.textContent = getDbTableFilterQuery() ? '无匹配的表' : '当前库中未找到用户表';
        return;
    }

    els.empty.hidden = true;
    els.list.hidden = false;
    els.list.innerHTML = '';

    filtered.forEach(t => {
        const tableName = String(t.tableName || '');
        const remarks = String(t.remarks || '');

        const details = document.createElement('details');
        details.className = 'db-table-row';
        details.dataset.tableName = tableName;
        details.setAttribute('role', 'listitem');

        const summary = document.createElement('summary');
        summary.className = 'db-table-row-summary';

        const main = document.createElement('div');
        main.className = 'db-table-row-main';
        const nameEl = document.createElement('div');
        nameEl.className = 'db-table-name';
        nameEl.textContent = tableName;
        main.appendChild(nameEl);
        if (remarks) {
            const remEl = document.createElement('div');
            remEl.className = 'db-table-remarks';
            remEl.textContent = remarks;
            main.appendChild(remEl);
        }

        const actions = document.createElement('div');
        actions.className = 'db-table-row-actions';

        const copyBtn = document.createElement('button');
        copyBtn.type = 'button';
        copyBtn.textContent = '复制';
        copyBtn.title = '复制表名';
        copyBtn.addEventListener('click', e => {
            e.preventDefault();
            e.stopPropagation();
            void copyDbTableName(tableName);
        });

        const insertBtn = document.createElement('button');
        insertBtn.type = 'button';
        insertBtn.textContent = '填入';
        insertBtn.title = '将表名追加到输入框';
        insertBtn.addEventListener('click', e => {
            e.preventDefault();
            e.stopPropagation();
            insertDbTableNameIntoInput(tableName);
        });

        actions.appendChild(copyBtn);
        actions.appendChild(insertBtn);

        summary.appendChild(main);
        summary.appendChild(actions);

        const slot = document.createElement('div');
        slot.className = 'db-table-detail db-table-detail-slot';

        details.appendChild(summary);
        details.appendChild(slot);
        details.addEventListener('toggle', () => {
            void onDbTableDetailsToggle(details, slot);
        });

        els.list.appendChild(details);
    });
}

async function copyDbTableName(name) {
    const text = String(name || '');
    if (!text) {
        return;
    }
    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(text);
        } else {
            throw new Error('no clipboard');
        }
    } catch (e) {
        try {
            const ta = document.createElement('textarea');
            ta.value = text;
            ta.setAttribute('readonly', '');
            ta.style.position = 'fixed';
            ta.style.left = '-9999px';
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            ta.remove();
        } catch (e2) {
            console.warn('copy failed', e2);
            showErrorDialog({title: '提示', message: '复制失败，请手动选择表名复制'});
        }
    }
}

function insertDbTableNameIntoInput(name) {
    const input = document.getElementById('userInput');
    if (!input || input.disabled) {
        showErrorDialog({title: '提示', message: '请先选择对话后再填入输入框'});
        return;
    }
    const t = String(name || '').trim();
    if (!t) {
        return;
    }
    const cur = String(input.value || '');
    input.value = cur ? `${cur} ${t}` : t;
    input.focus();
}

function columnNullableLabel(col) {
    if (col && (col.isNullable === true || col.nullable === true)) {
        return 'Y';
    }
    if (col && (col.isNullable === false || col.nullable === false)) {
        return 'N';
    }
    return '';
}

function buildTableSchemaHtml(table) {
    const cols = table && table.tableColumnList;
    if (!table || !Array.isArray(cols) || cols.length === 0) {
        return '<p class="db-table-detail-error">暂无列信息</p>';
    }
    const rows = cols
        .map(c => {
            const pk = c.pk === true || c.isPk === true ? 'Y' : '';
            const typeParts = [c.columnType, c.columnSize].filter(Boolean);
            const typeStr = typeParts.join(' ');
            return `<tr>
<td>${escapeHtml(c.columnName || '')}</td>
<td>${escapeHtml(typeStr)}</td>
<td>${escapeHtml(columnNullableLabel(c))}</td>
<td>${escapeHtml(pk)}</td>
<td>${escapeHtml(c.remarks || '')}</td>
</tr>`;
        })
        .join('');
    return `<div class="db-table-detail-table-wrap"><table class="db-table-detail-table"><thead><tr>
<th>列名</th><th>类型</th><th>可空</th><th>主键</th><th>注释</th>
</tr></thead><tbody>${rows}</tbody></table></div>`;
}

async function onDbTableDetailsToggle(details, slot) {
    if (!details.open) {
        return;
    }
    if (slot.dataset.loaded === '1') {
        return;
    }
    const tableName = details.dataset.tableName;
    const sid = currentSessionId;
    if (!tableName || !sid) {
        return;
    }
    slot.innerHTML = '<p class="db-table-detail-loading">加载表结构…</p>';
    try {
        const table = await Api.get('/dataBase/query/unique', {sessionId: sid, tableName});
        if (!details.open) {
            return;
        }
        if (!table) {
            slot.innerHTML = '<p class="db-table-detail-error">未获取到表结构</p>';
            return;
        }
        slot.dataset.loaded = '1';
        slot.innerHTML = buildTableSchemaHtml(table);
    } catch (e) {
        if (!details.open) {
            return;
        }
        slot.innerHTML = `<p class="db-table-detail-error">${escapeHtml(e.message || '加载失败')}</p>`;
        notifyErrorUnlessShown(e, '加载表结构失败');
    }
}

async function loadDbTableListForCurrentSession(options) {
    const keepSearch = options && options.keepSearch;
    const sid = currentSessionId;
    syncDbTableSearchAndRefreshDisabled();

    if (!sid || !sessions.some(s => s.id === sid)) {
        resetDbTablePanelToPlaceholder();
        return;
    }

    const els = getDbTableUiEls();
    if (!keepSearch && els.search) {
        els.search.value = '';
    }

    const seq = ++dbTableLoadSeq;
    if (els.placeholder) {
        els.placeholder.hidden = true;
    }
    if (els.loading) {
        els.loading.hidden = false;
    }
    if (els.empty) {
        els.empty.hidden = true;
    }
    if (els.list) {
        els.list.innerHTML = '';
        els.list.hidden = true;
    }
    if (els.refreshBtn) {
        els.refreshBtn.disabled = true;
    }

    try {
        const data = await Api.get('/dataBase/query/list', {sessionId: sid});
        if (seq !== dbTableLoadSeq) {
            return;
        }
        dbTableListRaw = Array.isArray(data) ? data : [];
        if (els.loading) {
            els.loading.hidden = true;
        }
        filterDbTableList();
    } catch (e) {
        if (seq !== dbTableLoadSeq) {
            return;
        }
        dbTableListRaw = [];
        if (els.loading) {
            els.loading.hidden = true;
        }
        if (els.placeholder) {
            els.placeholder.hidden = false;
            els.placeholder.textContent = '表列表加载失败，请点击刷新重试';
        }
        if (els.list) {
            els.list.innerHTML = '';
            els.list.hidden = true;
        }
        if (els.empty) {
            els.empty.hidden = true;
        }
        notifyErrorUnlessShown(e, '加载数据库表列表失败');
    } finally {
        if (seq === dbTableLoadSeq && els.refreshBtn) {
            els.refreshBtn.disabled = !currentSessionId || !sessions.some(s => s.id === currentSessionId);
        }
    }
}

async function refreshDbTableList() {
    await loadDbTableListForCurrentSession({keepSearch: true});
}

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
            resetDbTablePanelToPlaceholder();
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

/** 从助手 Markdown 中取出第一个 ```sql … ``` 或首个 ``` … ``` 代码块内容 */
function extractSqlFromAssistantMarkdown(md) {
    const text = String(md ?? '');
    let m = /```\s*sql\s*([\s\S]*?)```/i.exec(text);
    if (m) {
        return m[1].trim();
    }
    m = /```\s*([\s\S]*?)```/i.exec(text);
    if (m) {
        return m[1].trim();
    }
    return '';
}

function triggerBlobDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || 'export.bin';
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

async function pollExportJobUntilDone(jobId, statusEl) {
    const maxAttempts = 200;
    for (let i = 0; i < maxAttempts; i++) {
        await delayMs(1500);
        const v = await Api.get('/db/export/jobs/query/unique', {id: jobId, sessionId: currentSessionId});
        if (v.status === 'READY' && v.downloadReady) {
            statusEl.textContent = '处理完成，正在下载…';
            const {blob, filename} = await Api.downloadGet('/db/export/jobs/download', {
                id: jobId,
                sessionId: currentSessionId
            });
            triggerBlobDownload(blob, filename);
            statusEl.textContent = '完成，已开始下载。';
            return;
        }
        if (v.status === 'FAILED') {
            statusEl.textContent = '失败：' + (v.errorMessage || '未知错误');
            return;
        }
        if (v.status === 'EXPIRED') {
            statusEl.textContent = '任务已过期。';
            return;
        }
        statusEl.textContent = '处理中：' + v.status + '…';
    }
    statusEl.textContent = '等待超时，请稍后重试。';
}

/** 打开导出弹窗时，用于回写气泡内状态 / 恢复按钮 */
let exportModalBubbleCtx = null;

function closeExportSqlModal() {
    const modal = document.getElementById('exportSqlModal');
    if (modal) {
        closeModalAnimated(modal);
    }
    if (exportModalBubbleCtx && exportModalBubbleCtx.bubbleBtn) {
        exportModalBubbleCtx.bubbleBtn.disabled = false;
    }
    exportModalBubbleCtx = null;
}

function openExportSqlModal(initialSql, format, bubbleCtx) {
    const modal = document.getElementById('exportSqlModal');
    const ta = document.getElementById('exportModalSqlInput');
    const fmt = document.getElementById('exportModalFormatSelect');
    const modalStatus = document.getElementById('exportModalStatus');
    if (!modal || !ta || !fmt) {
        return;
    }
    exportModalBubbleCtx = bubbleCtx;
    ta.value = initialSql || '';
    fmt.value = format === 'XLSX' ? 'XLSX' : 'CSV';
    if (modalStatus) {
        modalStatus.textContent = '';
    }
    const confirmBtn = document.getElementById('exportModalConfirmBtn');
    const cancelBtn = document.getElementById('exportModalCancelBtn');
    if (confirmBtn) {
        confirmBtn.disabled = false;
    }
    if (cancelBtn) {
        cancelBtn.disabled = false;
    }
    openModalAnimated(modal);
    setTimeout(() => ta.focus(), 80);
}

async function confirmExportFromModal() {
    const ta = document.getElementById('exportModalSqlInput');
    const fmt = document.getElementById('exportModalFormatSelect');
    const modalStatus = document.getElementById('exportModalStatus');
    const cancelBtn = document.getElementById('exportModalCancelBtn');
    const confirmBtn = document.getElementById('exportModalConfirmBtn');
    const sql = String(ta && ta.value ? ta.value : '').trim();
    if (!sql) {
        showErrorDialog({title: '提示', message: 'SQL 不能为空。'});
        return;
    }
    if (!currentSessionId) {
        showErrorDialog({title: '提示', message: '请先选择对话。'});
        return;
    }
    if (isDbChatStreaming) {
        showErrorDialog({title: '提示', message: '请等待当前对话流结束后再导出。'});
        return;
    }
    const ctx = exportModalBubbleCtx;
    const format = String(fmt && fmt.value ? fmt.value : 'CSV').trim();
    const body = {sessionId: currentSessionId, sql, format};
    if (confirmBtn) {
        confirmBtn.disabled = true;
    }
    if (cancelBtn) {
        cancelBtn.disabled = true;
    }
    if (ctx && ctx.bubbleBtn) {
        ctx.bubbleBtn.disabled = true;
    }
    if (modalStatus) {
        modalStatus.textContent = '创建任务中…';
    }
    try {
        const view = await Api.post('/db/export/jobs/add', body);
        if (modalStatus) {
            modalStatus.textContent = '排队处理中…';
        }
        await pollExportJobUntilDone(view.id, modalStatus);
        if (ctx && ctx.statusEl && modalStatus) {
            ctx.statusEl.textContent = modalStatus.textContent;
        }
        closeExportSqlModal();
    } catch (e) {
        console.error('export job failed', e);
        notifyErrorUnlessShown(e, '导出失败');
        if (modalStatus) {
            modalStatus.textContent = '失败：' + (e.message || String(e));
        }
        if (ctx && ctx.statusEl) {
            ctx.statusEl.textContent = modalStatus ? modalStatus.textContent : '导出失败';
        }
    } finally {
        if (confirmBtn) {
            confirmBtn.disabled = false;
        }
        if (cancelBtn) {
            cancelBtn.disabled = false;
        }
        if (ctx && ctx.bubbleBtn) {
            ctx.bubbleBtn.disabled = false;
        }
    }
}

function initExportSqlModal() {
    const modal = document.getElementById('exportSqlModal');
    if (!modal) {
        return;
    }
    document.getElementById('exportModalCancelBtn')?.addEventListener('click', () => closeExportSqlModal());
    document.getElementById('exportModalConfirmBtn')?.addEventListener('click', () => void confirmExportFromModal());
    modal.addEventListener('click', e => {
        if (e.target !== modal) {
            return;
        }
        const confirmBtn = document.getElementById('exportModalConfirmBtn');
        if (confirmBtn && confirmBtn.disabled) {
            return;
        }
        closeExportSqlModal();
    });
}

/**
 * 在助手气泡底部挂载导出条（仅当正文 Markdown 中含可解析的 SQL 代码块时）。
 * @param {HTMLElement} messageDiv .bot-message 根节点
 * @param {string} answerMarkdown 用于提取 SQL 的原文（与气泡展示一致）
 */
function maybeBotExportBar(messageDiv, answerMarkdown) {
    if (!messageDiv || !messageDiv.classList || !messageDiv.classList.contains('bot-message')) {
        return;
    }
    messageDiv.querySelectorAll('.bot-export-bar').forEach(el => el.remove());
    const sql = extractSqlFromAssistantMarkdown(answerMarkdown || '');
    if (!sql) {
        return;
    }
    const bar = document.createElement('div');
    bar.className = 'bot-export-bar';
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-primary bot-export-btn';
    btn.textContent = '导出';
    const statusEl = document.createElement('span');
    statusEl.className = 'bot-export-status';
    btn.addEventListener('click', () =>
        openExportSqlModal(sql, 'CSV', {bubbleStatusEl: statusEl, bubbleBtn: btn})
    );
    bar.appendChild(btn);
    bar.appendChild(statusEl);
    messageDiv.appendChild(bar);
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
        resetDbTablePanelToPlaceholder();
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
        const sendBtn = document.getElementById('sendMessageBtn');
        if (btn) {
            btn.disabled = !!(sendBtn && sendBtn.disabled);
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
    resetDbTablePanelToPlaceholder();
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
        loadDbTableListForCurrentSession();
    } else {
        resetDbTablePanelToPlaceholder();
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

    if (role === 'bot') {
        const md =
            content && typeof content === 'object'
                ? String(content.content || '')
                : String(content || '');
        maybeBotExportBar(messageDiv, md);
    }

    chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
    return messageDiv;
}

async function sendMessage() {
    const inputElement = document.getElementById('userInput');
    const message = inputElement.value.trim();

    if (!message) return;
    if (isDbChatStreaming) return;

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

    isDbChatStreaming = true;
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

        maybeBotExportBar(botMessageDiv, answerAccum);

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
        isDbChatStreaming = false;
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
    if (isDbChatStreaming) {
        event.preventDefault();
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

const CHAT_DRAWER_LEFT_OPEN_KEY = 'chatDrawerLeftOpen';
const CHAT_DRAWER_RIGHT_OPEN_KEY = 'chatDrawerRightOpen';

function readChatDrawerPref(key, defaultOpen) {
    try {
        const v = localStorage.getItem(key);
        if (v === null) {
            return defaultOpen;
        }
        return v === '1' || v === 'true';
    } catch (e) {
        return defaultOpen;
    }
}

function setLeftDrawerOpen(open) {
    const shell = document.getElementById('chatLeftShell');
    const panel = document.getElementById('chatLeftPanel');
    const expandBtn = document.getElementById('chatLeftExpandBtn');
    if (!shell) {
        return;
    }
    if (open) {
        shell.classList.remove('is-collapsed');
    } else {
        shell.classList.add('is-collapsed');
    }
    try {
        localStorage.setItem(CHAT_DRAWER_LEFT_OPEN_KEY, open ? '1' : '0');
    } catch (e) {
        /* ignore */
    }
    if (panel) {
        panel.setAttribute('aria-hidden', open ? 'false' : 'true');
    }
    if (expandBtn) {
        expandBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    }
}

function setRightDrawerOpen(open) {
    const shell = document.getElementById('chatRightShell');
    const panel = document.getElementById('dbTableRightPanel');
    const expandBtn = document.getElementById('chatRightExpandBtn');
    if (!shell) {
        return;
    }
    if (open) {
        shell.classList.remove('is-collapsed');
    } else {
        shell.classList.add('is-collapsed');
    }
    try {
        localStorage.setItem(CHAT_DRAWER_RIGHT_OPEN_KEY, open ? '1' : '0');
    } catch (e) {
        /* ignore */
    }
    if (panel) {
        panel.setAttribute('aria-hidden', open ? 'false' : 'true');
    }
    if (expandBtn) {
        expandBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    }
}

function initChatDrawers() {
    setLeftDrawerOpen(readChatDrawerPref(CHAT_DRAWER_LEFT_OPEN_KEY, true));
    setRightDrawerOpen(readChatDrawerPref(CHAT_DRAWER_RIGHT_OPEN_KEY, true));

    document.getElementById('chatLeftCollapseBtn')?.addEventListener('click', () => setLeftDrawerOpen(false));
    document.getElementById('chatLeftExpandBtn')?.addEventListener('click', () => setLeftDrawerOpen(true));
    document.getElementById('chatRightCollapseBtn')?.addEventListener('click', () => setRightDrawerOpen(false));
    document.getElementById('chatRightExpandBtn')?.addEventListener('click', () => setRightDrawerOpen(true));
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initChatKeyboardShortcuts);
    document.addEventListener('DOMContentLoaded', initExportSqlModal);
    document.addEventListener('DOMContentLoaded', initChatDrawers);
} else {
    initChatKeyboardShortcuts();
    initExportSqlModal();
    initChatDrawers();
}
