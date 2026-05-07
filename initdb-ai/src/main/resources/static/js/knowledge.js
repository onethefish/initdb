/* global Api, escapeHtml, normalizePagePayload, notifyErrorUnlessShown, showErrorDialog, openModalAnimated, closeModalAnimated */
'use strict';

let contextDatasourceId = '';
let knowledgeList = [];
let knowledgePageNum = 1;
let knowledgePageSize = 10;
let knowledgeTotal = 0;
let editingKnowledgeId = null;
let editingKnowledgeType = '';
const selectedKnowledgeIds = new Set();

function normalizeTypeSelectValue(stored) {
    if (stored == null || String(stored).trim() === '') return '';
    const s = String(stored).trim().toLowerCase();
    if (s === 'mysql' || s.includes('mysql')) return 'mysql';
    if (s.includes('postgres')) return 'postgresql';
    return '';
}

function formatDbTypeLabel(type) {
    const raw = type != null ? String(type).trim() : '';
    if (!raw) return '';
    const n = normalizeTypeSelectValue(raw);
    if (n === 'mysql') return 'MySQL';
    if (n === 'postgresql') return 'PostgreSQL';
    return raw;
}

/** 知识类型：图标 + 短文案（类名仅允许已知枚举） */
function mapKnowledgeTypeBadge(row) {
    const code = String(row.type || '').trim().toUpperCase();
    const codeSafe = ['DOCUMENT', 'QA', 'FAQ'].includes(code) ? code.toLowerCase() : 'unk';
    const shortMap = {DOCUMENT: '文档', QA: '问答', FAQ: '常见'};
    const shortTxt = shortMap[code] || (row.typeValue ? String(row.typeValue).slice(0, 4) : (row.type ? String(row.type).slice(0, 4) : '—'));
    const iconMap = {DOCUMENT: '📄', QA: '💬', FAQ: '📋'};
    const icon = iconMap[code] || '📌';
    const titleSrc = row.typeValue || ({DOCUMENT: '文档', QA: '问答', FAQ: '常见问题'}[code]) || row.type || '';
    return `<span class="kb-enum kb-type kb-type--${codeSafe}" title="${escapeHtml(String(titleSrc))}"><span class="kb-enum__ico" aria-hidden="true">${icon}</span><span class="kb-enum__txt">${escapeHtml(shortTxt)}</span></span>`;
}

/** 向量化状态：图标 + 文案 */
function mapEmbeddingBadge(code) {
    const n = Number(code);
    const defs = {
        0: {cls: 'kb-emb--pending', icon: '⏸', label: '待处理'},
        1: {cls: 'kb-emb--run', icon: '⏳', label: '处理中'},
        2: {cls: 'kb-emb--ok', icon: '✓', label: '已完成'},
        3: {cls: 'kb-emb--fail', icon: '✕', label: '失败'}
    };
    const d = defs[n];
    if (!d) {
        return '<span class="kb-enum kb-emb kb-emb--unk" title="未知"><span class="kb-enum__ico" aria-hidden="true">—</span></span>';
    }
    return `<span class="kb-enum kb-emb ${d.cls}" title="${escapeHtml(d.label)}"><span class="kb-enum__ico" aria-hidden="true">${d.icon}</span><span class="kb-enum__lbl">${escapeHtml(d.label)}</span></span>`;
}

/** 向量化列：失败时可点「详情」看 errorMsg */
function mapEmbeddingColumnHtml(row) {
    const badge = mapEmbeddingBadge(row.embeddingStatus);
    if (Number(row.embeddingStatus) !== 3) {
        return badge;
    }
    return `<div class="kb-emb-cell-wrap">${badge}<button type="button" class="kb-emb-error-btn" title="查看错误详情">详情</button></div>`;
}

function openKbEmbeddingErrorModal(row) {
    const modal = document.getElementById('kbEmbeddingErrorModal');
    const titleLine = document.getElementById('kbEmbeddingErrorTitle');
    const bodyEl = document.getElementById('kbEmbeddingErrorBody');
    if (!modal || !titleLine || !bodyEl) return;
    const t = row && row.title != null ? String(row.title).trim() : '';
    titleLine.textContent = t || '（无标题）';
    const msg = row && row.errorMsg != null ? String(row.errorMsg).trim() : '';
    bodyEl.textContent = msg || '暂无详细错误信息。';
    openModalAnimated(modal);
}

function closeKbEmbeddingErrorModal() {
    const modal = document.getElementById('kbEmbeddingErrorModal');
    if (modal) closeModalAnimated(modal);
}

function mapRecallLabel(isRecall) {
    if (Number(isRecall) === 1) return '<span class="status-tag status-enabled">是</span>';
    if (Number(isRecall) === 0) return '<span class="status-tag test-unknown">否</span>';
    return '—';
}

function getKnowledgeFilters() {
    const type = document.getElementById('kbFilterType').value.trim();
    const emb = document.getElementById('kbFilterEmbedding').value.trim();
    const recall = document.getElementById('kbFilterRecall').value.trim();
    const params = {
        datasourceId: contextDatasourceId,
        title: document.getElementById('kbFilterTitle').value.trim()
    };
    if (type) params.type = type;
    if (emb !== '') params.embeddingStatus = Number(emb);
    if (recall !== '') params.isRecall = Number(recall);
    return params;
}

function updateKnowledgePageInfo() {
    const totalPages = knowledgeTotal > 0 ? Math.ceil(knowledgeTotal / knowledgePageSize) : 1;
    const el = document.getElementById('knowledgePageInfo');
    if (el) {
        el.textContent = `第 ${knowledgePageNum} 页 / 共 ${totalPages} 页（${knowledgeTotal} 条）`;
    }
}

function renderKnowledgeTable() {
    const tbody = document.getElementById('knowledgeTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    selectedKnowledgeIds.clear();
    const selectAll = document.getElementById('selectAllKnowledge');
    if (selectAll) selectAll.checked = false;

    if (!knowledgeList.length) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td colspan="8" class="empty-hint">暂无知识条目，请点击「新增」添加</td>';
        tbody.appendChild(tr);
        updateKnowledgePageInfo();
        return;
    }

    knowledgeList.forEach(row => {
        const tr = document.createElement('tr');
        const id = row.id != null ? String(row.id) : '';
        const idAttr = escapeHtml(id);
        const qPreview = escapeHtml((row.question || '').slice(0, 160));
        const fileHint = row.fileType || row.fileId ? escapeHtml(String(row.fileType || row.fileId || '').slice(0, 24)) : '—';
        tr.innerHTML = `
            <td><input data-id="${idAttr}" type="checkbox" onchange="toggleKnowledgeSelection('${id.replace(/'/g, '\\\'')}', this.checked)"></td>
            <td class="knowledge-cell-preview knowledge-cell-title" title="${escapeHtml(row.title || '')}">${row.title ? escapeHtml(row.title) : '—'}</td>
            <td>${mapKnowledgeTypeBadge(row)}</td>
            <td class="knowledge-cell-preview knowledge-cell-question" title="${escapeHtml(row.question || '')}">${qPreview || '—'}</td>
            <td>${mapEmbeddingColumnHtml(row)}</td>
            <td>${mapRecallLabel(row.isRecall)}</td>
            <td class="knowledge-cell-preview">${fileHint}</td>
            <td>
                <div class="table-actions">
                  <button class="ds-row-action ds-row-action--edit" type="button" onclick="openKnowledgeModal('${id.replace(/'/g, '\\\'')}')">编辑</button>
                  <button class="ds-row-action ds-row-action--test" type="button" onclick="refreshKnowledge('${id.replace(/'/g, '\\\'')}')">刷新向量</button>
                  <button class="ds-row-action ds-row-action--delete" type="button" onclick="deleteKnowledge('${id.replace(/'/g, '\\\'')}')">删除</button>
                </div>
            </td>
        `;
        const errDetBtn = tr.querySelector('.kb-emb-error-btn');
        if (errDetBtn) {
            errDetBtn.addEventListener('click', () => openKbEmbeddingErrorModal(row));
        }
        tbody.appendChild(tr);
    });
    updateKnowledgePageInfo();
}

async function queryKnowledgePage() {
    if (!contextDatasourceId) return;
    try {
        const params = {
            ...getKnowledgeFilters(),
            pageNum: knowledgePageNum,
            pageSize: knowledgePageSize
        };
        const raw = await Api.get('/agentKnowledge/query/page', params);
        const pageData = normalizePagePayload(raw);
        knowledgeList = pageData.records;
        knowledgeTotal = pageData.total;
        knowledgePageNum = pageData.current || knowledgePageNum;
        knowledgePageSize = pageData.size || knowledgePageSize;
        renderKnowledgeTable();
    } catch (error) {
        console.error('Query knowledge page error:', error);
        notifyErrorUnlessShown(error, '查询知识库失败');
    }
}

function resetKnowledgeFilters() {
    document.getElementById('kbFilterTitle').value = '';
    document.getElementById('kbFilterType').value = '';
    document.getElementById('kbFilterEmbedding').value = '';
    document.getElementById('kbFilterRecall').value = '';
    knowledgePageNum = 1;
    queryKnowledgePage();
}

function changeKnowledgePage(direction) {
    const totalPages = knowledgeTotal > 0 ? Math.ceil(knowledgeTotal / knowledgePageSize) : 1;
    const next = knowledgePageNum + direction;
    if (next < 1 || next > totalPages) return;
    knowledgePageNum = next;
    queryKnowledgePage();
}

function toggleKnowledgeSelection(id, checked) {
    if (checked) selectedKnowledgeIds.add(id);
    else selectedKnowledgeIds.delete(id);
}

function toggleSelectAllKnowledge(checkbox) {
    const checked = !!checkbox.checked;
    document.querySelectorAll('#knowledgeTableBody input[type="checkbox"]').forEach(item => {
        item.checked = checked;
        const id = item.dataset.id;
        if (!id) return;
        if (checked) selectedKnowledgeIds.add(id);
        else selectedKnowledgeIds.delete(id);
    });
}

function syncKnowledgeModalFields() {
    const type = document.getElementById('kbModalType').value;
    const isEdit = !!editingKnowledgeId;
    const qGroup = document.getElementById('kbModalQuestionGroup');
    const cGroup = document.getElementById('kbModalContentGroup');
    const fileGroup = document.getElementById('kbModalFileGroup');
    const splitGroup = document.getElementById('kbModalSplitterGroup');
    const typeSelect = document.getElementById('kbModalType');

    if (type === 'DOCUMENT') {
        qGroup.hidden = true;
        cGroup.hidden = true;
        fileGroup.hidden = isEdit;
        splitGroup.hidden = isEdit;
        typeSelect.disabled = isEdit;
    } else {
        qGroup.hidden = false;
        cGroup.hidden = false;
        fileGroup.hidden = true;
        splitGroup.hidden = true;
        typeSelect.disabled = isEdit;
    }
}

function resetKnowledgeModalForm() {
    document.getElementById('kbModalType').value = 'DOCUMENT';
    document.getElementById('kbModalTitle').value = '';
    document.getElementById('kbModalQuestion').value = '';
    document.getElementById('kbModalContent').value = '';
    document.getElementById('kbModalFile').value = '';
    document.getElementById('kbModalSplitter').value = 'token';
    document.getElementById('kbModalQuestion').disabled = false;
    syncKnowledgeModalFields();
}

async function openKnowledgeModal(id) {
    editingKnowledgeId = id || null;
    document.getElementById('knowledgeModalTitle').textContent = editingKnowledgeId ? '编辑知识' : '新增';
    document.getElementById('kbModalType').disabled = false;

    if (!editingKnowledgeId) {
        resetKnowledgeModalForm();
        openModalAnimated(document.getElementById('knowledgeModal'));
        return;
    }

    try {
        const row = await Api.get('/agentKnowledge/query/unique', {id: editingKnowledgeId});
        if (!row) {
            showErrorDialog({title: '提示', message: '未找到该知识条目'});
            return;
        }
        editingKnowledgeType = row.type || '';
        document.getElementById('kbModalType').value = editingKnowledgeType || 'DOCUMENT';
        document.getElementById('kbModalType').disabled = true;
        document.getElementById('kbModalTitle').value = row.title || '';
        document.getElementById('kbModalQuestion').value = row.question || '';
        document.getElementById('kbModalContent').value = row.content || '';
        document.getElementById('kbModalFile').value = '';
        const qEl = document.getElementById('kbModalQuestion');
        qEl.disabled = true;
        syncKnowledgeModalFields();
        openModalAnimated(document.getElementById('knowledgeModal'));
    } catch (error) {
        console.error('Query knowledge unique error:', error);
        notifyErrorUnlessShown(error, '加载知识详情失败');
    }
}

function closeKnowledgeModal() {
    editingKnowledgeId = null;
    editingKnowledgeType = '';
    document.getElementById('kbModalType').disabled = false;
    document.getElementById('kbModalQuestion').disabled = false;
    closeModalAnimated(document.getElementById('knowledgeModal'));
}

function resetVectorSearchModal() {
    const q = document.getElementById('kbVectorQuery');
    const t = document.getElementById('kbVectorType');
    const group = document.getElementById('vectorSearchResultsGroup');
    const box = document.getElementById('vectorSearchResults');
    if (q) q.value = '';
    if (t) t.value = '';
    if (group) group.hidden = true;
    if (box) box.innerHTML = '';
}

function openVectorSearchModal() {
    if (!contextDatasourceId) {
        showErrorDialog({title: '提示', message: '请先进入某一数据源的「知识库管理」页签'});
        return;
    }
    const modal = document.getElementById('vectorSearchModal');
    if (!modal) return;
    resetVectorSearchModal();
    const typeEl = document.getElementById('kbVectorType');
    const filterType = document.getElementById('kbFilterType');
    if (typeEl && filterType && filterType.value) {
        typeEl.value = filterType.value;
    }
    openModalAnimated(modal);
}

function closeVectorSearchModal() {
    const modal = document.getElementById('vectorSearchModal');
    if (modal) closeModalAnimated(modal);
    resetVectorSearchModal();
}

function pickVectorDocumentText(doc) {
    if (!doc || typeof doc !== 'object') return '';
    if (typeof doc.text === 'string') return doc.text;
    if (doc.text != null) return String(doc.text);
    return '';
}

function renderVectorSearchResults(list) {
    const group = document.getElementById('vectorSearchResultsGroup');
    const box = document.getElementById('vectorSearchResults');
    if (!group || !box) return;
    const docs = Array.isArray(list) ? list : [];
    group.hidden = false;
    if (!docs.length) {
        box.innerHTML = '<p class="empty-hint">未找到相近的向量片段（请确认内容已向量化完成）。</p>';
        return;
    }
    box.innerHTML = docs.map((doc, idx) => {
        const text = escapeHtml(pickVectorDocumentText(doc));
        const meta = doc.metadata && typeof doc.metadata === 'object'
            ? escapeHtml(JSON.stringify(doc.metadata, null, 2))
            : '—';
        const idHint = doc.id != null ? escapeHtml(String(doc.id)) : String(idx + 1);
        return `<div class="vector-search-result-item">
            <div class="vector-doc-text">${text || '（无文本）'}</div>
            <div class="vector-doc-meta">片段 ID：<code>${idHint}</code></div>
            <pre class="vector-doc-meta-json"><code>${meta}</code></pre>
        </div>`;
    }).join('');
}

async function submitVectorSearch() {
    if (!contextDatasourceId) {
        showErrorDialog({title: '提示', message: '缺少数据源上下文'});
        return;
    }
    const query = document.getElementById('kbVectorQuery').value.trim();
    if (!query) {
        showErrorDialog({title: '提示', message: '请填写检索描述'});
        return;
    }
    const type = document.getElementById('kbVectorType').value.trim();
    const params = {
        datasourceId: contextDatasourceId,
        query
    };
    if (type) params.type = type;
    if (selectedKnowledgeIds.size === 1) {
        const onlyId = Array.from(selectedKnowledgeIds)[0];
        if (onlyId) params.id = onlyId;
    }
    try {
        const raw = await Api.get('/agentVector/query/list', params);
        renderVectorSearchResults(raw);
    } catch (error) {
        console.error('Vector search error:', error);
        notifyErrorUnlessShown(error, '向量检索失败');
    }
}

async function submitKnowledgeModal() {
    const title = document.getElementById('kbModalTitle').value.trim();
    if (!title) {
        showErrorDialog({title: '提示', message: '请填写标题'});
        return;
    }

    if (editingKnowledgeId) {
        try {
            await Api.put('/agentKnowledge/update', {
                id: editingKnowledgeId,
                datasourceId: contextDatasourceId,
                title,
                type: editingKnowledgeType,
                question: document.getElementById('kbModalQuestion').value,
                content: document.getElementById('kbModalContent').value
            });
            closeKnowledgeModal();
            await queryKnowledgePage();
        } catch (error) {
            console.error('Update knowledge error:', error);
            notifyErrorUnlessShown(error, '保存失败');
        }
        return;
    }

    const type = document.getElementById('kbModalType').value;
    const question = document.getElementById('kbModalQuestion').value.trim();
    const content = document.getElementById('kbModalContent').value.trim();
    const fileInput = document.getElementById('kbModalFile');
    const file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
    const splitterType = document.getElementById('kbModalSplitter').value.trim() || 'token';

    if (type === 'DOCUMENT') {
        if (!file) {
            showErrorDialog({title: '提示', message: '文档类型请上传文件'});
            return;
        }
    } else {
        if (!question || !content) {
            showErrorDialog({title: '提示', message: '问答 / 常见问题类型请填写「问题」与「内容」'});
            return;
        }
    }

    const formData = new FormData();
    formData.append('datasourceId', contextDatasourceId);
    formData.append('title', title);
    formData.append('type', type);
    if (question) formData.append('question', question);
    if (content) formData.append('content', content);
    if (file) formData.append('file', file);
    if (type === 'DOCUMENT') {
        formData.append('splitterType', splitterType);
    }

    try {
        await Api.requestForm('/agentKnowledge/add', formData);
        closeKnowledgeModal();
        await queryKnowledgePage();
    } catch (error) {
        console.error('Add knowledge error:', error);
        notifyErrorUnlessShown(error, '新增失败');
    }
}

async function deleteKnowledge(id) {
    if (!confirm('确认删除该知识条目吗？')) return;
    try {
        await Api.del('/agentKnowledge/delete', {body: {id, datasourceId: contextDatasourceId}});
        await queryKnowledgePage();
    } catch (error) {
        console.error('Delete knowledge error:', error);
        notifyErrorUnlessShown(error, '删除失败');
    }
}

async function refreshKnowledge(id) {
    if (!id || !contextDatasourceId) return;
    if (!confirm('确认对该条目重新发起向量化？处理为异步，完成后请刷新列表查看状态。')) return;
    try {
        await Api.post('/agentKnowledge/refresh', {id, datasourceId: contextDatasourceId});
        await queryKnowledgePage();
    } catch (error) {
        console.error('Refresh knowledge embedding error:', error);
        notifyErrorUnlessShown(error, '刷新向量失败');
    }
}

async function refreshSelectedKnowledge() {
    if (!selectedKnowledgeIds.size) {
        showErrorDialog({title: '提示', message: '请先勾选要刷新的条目'});
        return;
    }
    if (!confirm(`确认对选中的 ${selectedKnowledgeIds.size} 条重新发起向量化？处理为异步，完成后请刷新列表查看状态。`)) return;
    const ids = Array.from(selectedKnowledgeIds);
    const body = ids.map(kid => ({id: kid, datasourceId: contextDatasourceId}));
    try {
        await Api.post('/agentKnowledge/refresh/batch', body);
        await queryKnowledgePage();
    } catch (error) {
        console.error('Batch refresh knowledge embedding error:', error);
        notifyErrorUnlessShown(error, '批量刷新向量失败');
    }
}

async function deleteSelectedKnowledge() {
    if (!selectedKnowledgeIds.size) {
        showErrorDialog({title: '提示', message: '请先选择要删除的条目'});
        return;
    }
    if (!confirm(`确认删除选中的 ${selectedKnowledgeIds.size} 条知识吗？`)) return;
    const ids = Array.from(selectedKnowledgeIds);
    const body = ids.map(id => ({id, datasourceId: contextDatasourceId}));
    try {
        await Api.del('/agentKnowledge/delete/batch', {body});
        selectedKnowledgeIds.clear();
        await queryKnowledgePage();
    } catch (error) {
        console.error('Batch delete knowledge error:', error);
        notifyErrorUnlessShown(error, '批量删除失败');
    }
}

function syncKnowledgeContextToWindow() {
    if (typeof window !== 'undefined') {
        window.__knowledgeDsId = contextDatasourceId || '';
    }
}

function setKnowledgeLayoutVisible(mode) {
    const invalid = document.getElementById('knowledgeInvalid');
    const body = document.getElementById('knowledgeBody');
    const bar = document.getElementById('knowledgeContextBar');
    if (!invalid) {
        if (body) body.hidden = false;
        if (bar) bar.hidden = false;
        return;
    }
    if (mode === 'invalid') {
        invalid.hidden = false;
        if (body) body.hidden = true;
        if (bar) bar.hidden = true;
    } else {
        invalid.hidden = true;
        if (body) body.hidden = false;
        if (bar) bar.hidden = false;
    }
}

async function initKnowledgeContextAndQuery() {
    try {
        const ds = await Api.get('/datasource/query/unique', {id: contextDatasourceId});
        const meta = document.getElementById('knowledgeContextMeta');
        if (meta && ds) {
            const name = ds.name != null ? String(ds.name) : '';
            const host = ds.host != null ? String(ds.host) : '';
            const port = ds.port != null ? String(ds.port) : '';
            const db = ds.databaseName != null ? String(ds.databaseName) : '';
            meta.textContent = name
                ? `${name}（${formatDbTypeLabel(ds.type)} · ${host}:${port} / ${db}）`
                : '当前数据源';
        }
    } catch (error) {
        console.error('Load datasource for context error:', error);
        const meta = document.getElementById('knowledgeContextMeta');
        if (meta) {
            meta.textContent = `数据源 ID：${contextDatasourceId}（详情加载失败）`;
        }
        notifyErrorUnlessShown(error, '加载数据源信息失败');
    }

    syncKnowledgeContextToWindow();
    knowledgePageNum = 1;
    await queryKnowledgePage();
}

async function initKnowledgePage() {
    const params = new URLSearchParams(window.location.search);
    // 与数据源列表一致：后端主键字段为 id；兼容历史链接 datasourceId
    contextDatasourceId = (params.get('id') || params.get('datasourceId') || '').trim();
    if (!contextDatasourceId) {
        setKnowledgeLayoutVisible('invalid');
        return;
    }

    setKnowledgeLayoutVisible('ok');
    await initKnowledgeContextAndQuery();
}

/**
 * 数据源页内嵌：由表格「知识库管理」或 ?kb= 进入。
 */
async function enterEmbeddedKnowledge(datasourceId) {
    if (!datasourceId || !document.getElementById('panelKnowledge')) return;
    contextDatasourceId = String(datasourceId).trim();
    const tab = document.getElementById('navTabKnowledge');
    if (tab) tab.classList.remove('top-tab--hidden');
    if (typeof switchDatasourceShellView === 'function') {
        switchDatasourceShellView('knowledge');
    }
    try {
        const u = new URL(window.location.href);
        u.searchParams.set('kb', contextDatasourceId);
        history.replaceState(null, '', u.pathname + '?' + u.searchParams.toString());
    } catch (_) {
        /* ignore */
    }
    await initKnowledgeContextAndQuery();
}

function clearEmbeddedKnowledgeContext() {
    contextDatasourceId = '';
    syncKnowledgeContextToWindow();
}

if (typeof window !== 'undefined') {
    window.enterEmbeddedKnowledge = enterEmbeddedKnowledge;
    window.clearEmbeddedKnowledgeContext = clearEmbeddedKnowledgeContext;
}
