/* global Api, escapeHtml, mapStatusTag, mapTestStatusTag, normalizePagePayload, notifyErrorUnlessShown, showErrorDialog, closeKnowledgeModal, openModalAnimated, closeModalAnimated */
'use strict';

let datasourceList = [];
let datasourcePageNum = 1;
let datasourcePageSize = 10;
let datasourceTotal = 0;
let editingDatasourceId = null;
/** 弹窗内当前认定的连接测试状态：接口返回或详情加载；保存时写入 testStatus */
let datasourceModalTestStatus = null;
/** 用户是否手动改过连接 URL；为 true 时不再随主机等自动改写 */
let datasourceUrlManualOverride = false;
let datasourceUrlBindDone = false;
const selectedDatasourceIds = new Set();

function normalizeTypeSelectValue(stored) {
    if (stored == null || String(stored).trim() === '') return '';
    const s = String(stored).trim().toLowerCase();
    if (s === 'mysql' || s.includes('mysql')) return 'mysql';
    if (s.includes('postgres')) return 'postgresql';
    return '';
}

/** 列表展示用：与下拉选项文案一致 */
function formatDbTypeLabel(type) {
    const raw = type != null ? String(type).trim() : '';
    if (!raw) return '';
    const n = normalizeTypeSelectValue(raw);
    if (n === 'mysql') return 'MySQL';
    if (n === 'postgresql') return 'PostgreSQL';
    return raw;
}

function buildSuggestedJdbcUrl(type, host, port, databaseName) {
    const t = (type || '').trim().toLowerCase();
    const h = (host || '').trim();
    const p = (port || '').trim();
    const db = (databaseName || '').trim();
    if (!t || !h || !p) return '';

    if (t === 'mysql') {
        return db ? `jdbc:mysql://${h}:${p}/${db}` : `jdbc:mysql://${h}:${p}/`;
    }
    if (t === 'postgresql') {
        return db ? `jdbc:postgresql://${h}:${p}/${db}` : `jdbc:postgresql://${h}:${p}/`;
    }
    return '';
}

function syncDatasourceConnectionUrl() {
    const type = document.getElementById('dsType').value.trim();
    const host = document.getElementById('dsHost').value.trim();
    const port = document.getElementById('dsPort').value.trim();
    const databaseName = document.getElementById('dsDatabaseName').value.trim();
    const urlEl = document.getElementById('dsConnectionUrl');
    if (!urlEl || datasourceUrlManualOverride) return;
    const next = buildSuggestedJdbcUrl(type, host, port, databaseName);
    urlEl.value = next;
}

function bindDatasourceUrlAutoSync() {
    if (datasourceUrlBindDone) return;
    const urlEl = document.getElementById('dsConnectionUrl');
    const typeEl = document.getElementById('dsType');
    if (!urlEl || !typeEl) return;
    datasourceUrlBindDone = true;

    urlEl.addEventListener('input', () => {
        datasourceUrlManualOverride = true;
    });

    ['dsHost', 'dsPort', 'dsDatabaseName'].forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        const run = () => {
            if (!datasourceUrlManualOverride) {
                syncDatasourceConnectionUrl();
            }
        };
        el.addEventListener('input', run);
        el.addEventListener('change', run);
    });

    typeEl.addEventListener('change', () => {
        datasourceUrlManualOverride = false;
        syncDatasourceConnectionUrl();
    });
}

function getDatasourceFilters() {
    return {
        name: document.getElementById('dsFilterName').value.trim(),
        type: document.getElementById('dsFilterType').value.trim(),
        host: document.getElementById('dsFilterHost').value.trim(),
        status: document.getElementById('dsFilterStatus').value
    };
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
        tr.innerHTML = '<td colspan="10" class="empty-hint">暂无数据源，请点击「新增」创建</td>';
        tbody.appendChild(tr);
        updateDatasourcePageInfo();
        return;
    }

    datasourceList.forEach(ds => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><input data-id="${ds.id}" type="checkbox" onchange="toggleDatasourceSelection('${ds.id}', this.checked)"></td>
            <td>${escapeHtml(ds.name)}</td>
            <td>${escapeHtml(formatDbTypeLabel(ds.type))}</td>
            <td>${escapeHtml(ds.host)}:${escapeHtml(ds.port)}</td>
            <td>${escapeHtml(ds.databaseName)}</td>
            <td>${escapeHtml(ds.username)}</td>
            <td>${mapStatusTag(ds.status)}</td>
            <td>${mapTestStatusTag(ds.testStatus)}</td>
            <td>${escapeHtml(ds.description)}</td>
            <td>
                <div class="table-actions">
                  <button class="ds-row-action ds-row-action--test" type="button" onclick="testDatasourceRow('${ds.id}')">测试</button>
                  <button class="ds-row-action ds-row-action--edit" type="button" onclick="openDatasourceModal('${ds.id}')">编辑</button>
                  <button class="ds-row-action ds-row-action--knowledge" type="button" onclick="openKnowledgeMaintenance('${ds.id}')">知识库管理</button>
                  <button class="ds-row-action ds-row-action--delete" type="button" onclick="deleteDatasource('${ds.id}')">删除</button>
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
        const raw = await Api.get('/datasource/query/page', params);
        const pageData = normalizePagePayload(raw);
        datasourceList = pageData.records;
        datasourceTotal = pageData.total;
        datasourcePageNum = pageData.current || datasourcePageNum;
        datasourcePageSize = pageData.size || datasourcePageSize;
        renderDatasourceTable();
    } catch (error) {
        console.error('Query datasource page error:', error);
        notifyErrorUnlessShown(error, '查询数据源失败');
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
    datasourceUrlManualOverride = false;
    document.getElementById('dsName').value = ds?.name || '';
    const typeNorm = normalizeTypeSelectValue(ds?.type);
    document.getElementById('dsType').value = typeNorm || '';
    document.getElementById('dsHost').value = ds?.host || '';
    document.getElementById('dsPort').value = ds?.port || '';
    document.getElementById('dsDatabaseName').value = ds?.databaseName || '';
    document.getElementById('dsUsername').value = ds?.username || '';
    document.getElementById('dsPassword').value = ds?.password || '';
    const urlFromServer = (ds?.connectionUrl || '').trim();
    document.getElementById('dsConnectionUrl').value = ds?.connectionUrl || '';
    if (urlFromServer) {
        datasourceUrlManualOverride = true;
    } else {
        syncDatasourceConnectionUrl();
    }
    document.getElementById('dsStatus').value = String(ds?.status ?? 1);
    document.getElementById('dsDescription').value = ds?.description || '';
    if (ds == null || ds.testStatus === undefined || ds.testStatus === null) {
        datasourceModalTestStatus = null;
    } else {
        datasourceModalTestStatus = Number(ds.testStatus);
    }
}

function collectDatasourceForm() {
    const body = {
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
    if (datasourceModalTestStatus != null && !Number.isNaN(datasourceModalTestStatus)) {
        body.testStatus = Number(datasourceModalTestStatus);
    }
    return body;
}

async function openDatasourceModal(id) {
    editingDatasourceId = id || null;
    document.getElementById('datasourceModalTitle').textContent = editingDatasourceId ? '编辑数据源' : '新增';

    if (!editingDatasourceId) {
        fillDatasourceForm(null);
        openModalAnimated(document.getElementById('datasourceModal'));
        return;
    }

    try {
        const ds = await Api.get('/datasource/query/unique', {id: editingDatasourceId});
        fillDatasourceForm(ds || {});
        openModalAnimated(document.getElementById('datasourceModal'));
    } catch (error) {
        console.error('Query datasource unique error:', error);
        notifyErrorUnlessShown(error, '加载数据源详情失败');
    }
}

function closeDatasourceModal() {
    editingDatasourceId = null;
    datasourceModalTestStatus = null;
    datasourceUrlManualOverride = false;
    closeModalAnimated(document.getElementById('datasourceModal'));
}

async function submitDatasourceForm() {
    if (!datasourceUrlManualOverride) {
        syncDatasourceConnectionUrl();
    }
    const body = collectDatasourceForm();
    if (!body.name || !body.type || !body.host || !body.port) {
        showErrorDialog({title: '提示', message: '名称、类型、主机、端口为必填项'});
        return;
    }
    try {
        if (editingDatasourceId) await Api.put('/datasource/update', body);
        else await Api.post('/datasource/add', body);
        closeDatasourceModal();
        await queryDatasourcePage();
    } catch (error) {
        console.error('Save datasource error:', error);
        notifyErrorUnlessShown(error, '保存数据源失败');
    }
}

async function testDatasourceConnection() {
    if (!datasourceUrlManualOverride) {
        syncDatasourceConnectionUrl();
    }
    const body = collectDatasourceForm();
    if (!body.connectionUrl?.trim()) {
        showErrorDialog({title: '提示', message: '请先填写连接 URL'});
        return;
    }
    try {
        const result = await Api.post('/datasource/test', body);
        if (result && typeof result === 'object' && result.testStatus != null) {
            datasourceModalTestStatus = Number(result.testStatus);
        } else {
            datasourceModalTestStatus = 1;
        }
        alert('连接测试成功');
    } catch (error) {
        console.error('Test datasource connection error:', error);
        datasourceModalTestStatus = 0;
        notifyErrorUnlessShown(error, '连接测试失败');
    }
}

async function testDatasourceRow(id) {
    try {
        const ds = await Api.get('/datasource/query/unique', {id});
        const result = await Api.post('/datasource/test', ds);
        const msg = result && typeof result === 'object' && result.testStatus === 1
            ? '连接测试成功'
            : '连接测试已完成';
        alert(msg);
        await queryDatasourcePage();
    } catch (error) {
        console.error('Test datasource row error:', error);
        notifyErrorUnlessShown(error, '连接测试失败');
    }
}

async function deleteDatasource(id) {
    if (!confirm('确认删除该数据源吗？')) return;
    try {
        await Api.del('/datasource/delete', {body: {id}});
        await queryDatasourcePage();
    } catch (error) {
        console.error('Delete datasource error:', error);
        notifyErrorUnlessShown(error, '删除数据源失败');
    }
}

async function deleteSelectedDatasource() {
    if (!selectedDatasourceIds.size) {
        showErrorDialog({title: '提示', message: '请先选择要删除的数据源'});
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
        notifyErrorUnlessShown(error, '批量删除失败');
    }
}

function openKnowledgeMaintenance(datasourceId) {
    if (datasourceId == null || String(datasourceId).trim() === '') return;
    if (typeof window.enterEmbeddedKnowledge === 'function' && document.getElementById('panelKnowledge')) {
        window.enterEmbeddedKnowledge(String(datasourceId).trim());
        return;
    }
}

function switchDatasourceShellView(view) {
    const panelDs = document.getElementById('panelDatasource');
    const panelKb = document.getElementById('panelKnowledge');
    const tabK = document.getElementById('navTabKnowledge');
    const tabD = document.getElementById('navTabDatasource');
    if (!panelDs || !panelKb || !tabD) return;
    if (view === 'knowledge') {
        panelDs.classList.remove('page-panel-active');
        panelKb.classList.add('page-panel-active');
        if (tabK) tabK.classList.add('active');
        tabD.classList.remove('active');
    } else {
        panelKb.classList.remove('page-panel-active');
        panelDs.classList.add('page-panel-active');
        if (tabK) tabK.classList.remove('active');
        tabD.classList.add('active');
    }
}

function stripKbQueryFromUrl() {
    try {
        const u = new URL(window.location.href);
        u.searchParams.delete('kb');
        const qs = u.searchParams.toString();
        history.replaceState(null, '', u.pathname + (qs ? '?' + qs : ''));
    } catch (_) {
        /* ignore */
    }
}

function hideKnowledgeTab() {
    const tabK = document.getElementById('navTabKnowledge');
    if (tabK) {
        tabK.classList.add('top-tab--hidden');
        tabK.classList.remove('active');
    }
}

/** 回到数据源列表：清 URL 参数、隐藏「知识库管理」顶栏页签、清空内嵌上下文 */
function returnToDatasourceList() {
    const km = document.getElementById('knowledgeModal');
    if (km && km.style.display !== 'none' && typeof closeKnowledgeModal === 'function') {
        closeKnowledgeModal();
    }
    switchDatasourceShellView('datasource');
    stripKbQueryFromUrl();
    hideKnowledgeTab();
    window.__knowledgeDsId = '';
    if (typeof window.clearEmbeddedKnowledgeContext === 'function') {
        window.clearEmbeddedKnowledgeContext();
    }
}

function bindDatasourceShellNav() {
    const tabK = document.getElementById('navTabKnowledge');
    const tabD = document.getElementById('navTabDatasource');
    if (!tabD || !document.getElementById('panelKnowledge')) return;

    tabD.addEventListener('click', e => {
        e.preventDefault();
        returnToDatasourceList();
    });

    if (tabK) {
        tabK.addEventListener('click', e => {
            e.preventDefault();
            const id = window.__knowledgeDsId;
            if (!id) return;
            switchDatasourceShellView('knowledge');
            try {
                const u = new URL(window.location.href);
                u.searchParams.set('kb', id);
                history.replaceState(null, '', u.pathname + '?' + u.searchParams.toString());
            } catch (_) {
                /* ignore */
            }
        });
    }
}

bindDatasourceShellNav();
bindDatasourceUrlAutoSync();
