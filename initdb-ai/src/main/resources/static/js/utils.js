'use strict';

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

/**
 * 兼容 MyBatis-Plus Page、Spring Data 风格及统一响应里再包一层 data 的 JSON。
 */
function normalizePagePayload(raw) {
    if (raw == null) {
        return {records: [], total: 0, current: 1, size: 10};
    }
    if (typeof raw === 'object' && !Array.isArray(raw) && raw.pageParam && Array.isArray(raw.records)) {
        const pp = raw.pageParam;
        return {
            records: raw.records,
            total: Number(pp.total ?? 0),
            current: Number(pp.pageNumber ?? pp.pageNum ?? 1),
            size: Number(pp.pageSize ?? pp.limit ?? 10)
        };
    }
    if (Array.isArray(raw)) {
        return {records: raw, total: raw.length, current: 1, size: raw.length};
    }
    if (typeof raw !== 'object') {
        return {records: [], total: 0, current: 1, size: 10};
    }

    let inner = raw;
    if (!Array.isArray(raw.records) && !Array.isArray(raw.list) && raw.data && typeof raw.data === 'object') {
        inner = raw.data;
    }

    let records = inner.records;
    if (!Array.isArray(records)) records = inner.list;
    if (!Array.isArray(records)) records = inner.content;
    if (!Array.isArray(records)) records = inner.rows;
    if (!Array.isArray(records)) records = [];

    const total = Number(inner.total ?? inner.totalElements ?? raw.total ?? raw.totalElements ?? 0);
    const current = Number(inner.current ?? inner.currentPage ?? inner.pageNum ?? raw.current ?? raw.pageNum ?? 1);
    const size = Number(inner.size ?? inner.pageSize ?? inner.limit ?? raw.size ?? raw.pageSize ?? 10);

    return {records, total, current, size};
}

function ensureGlobalErrorModal() {
    if (document.getElementById('globalErrorModal')) return;

    const modal = document.createElement('div');
    modal.id = 'globalErrorModal';
    modal.className = 'modal global-error-modal';
    modal.setAttribute('role', 'alertdialog');
    modal.setAttribute('aria-modal', 'true');
    modal.innerHTML = `
<div class="modal-content error-dialog-content">
  <h2 id="globalErrorTitle">错误</h2>
  <p id="globalErrorMessage" class="error-dialog-message"></p>
  <div id="globalErrorMeta" class="error-dialog-meta" hidden></div>
  <div class="modal-buttons">
    <button type="button" class="btn btn-primary" id="globalErrorOk">确定</button>
  </div>
</div>`;
    document.body.appendChild(modal);

    const close = () => {
        modal.style.display = 'none';
    };
    document.getElementById('globalErrorOk').addEventListener('click', close);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) close();
    });
}

/**
 * 统一错误 / 提示弹窗（与业务表单 modal 样式一致）。
 * @param {{ title?: string, message: string, code?: string|number, traceId?: string }} opts
 */
function showErrorDialog(opts) {
    const o = opts || {};
    const message = (o.message != null && String(o.message).trim() !== '')
        ? String(o.message)
        : '操作失败';
    ensureGlobalErrorModal();
    const modal = document.getElementById('globalErrorModal');
    const titleEl = document.getElementById('globalErrorTitle');
    const msgEl = document.getElementById('globalErrorMessage');
    const metaEl = document.getElementById('globalErrorMeta');

    titleEl.textContent = o.title || '错误';
    msgEl.textContent = message;

    const parts = [];
    if (o.code !== undefined && o.code !== null && String(o.code).trim() !== '') {
        parts.push(`错误码：${o.code}`);
    }
    if (o.traceId) {
        parts.push(`traceId：${o.traceId}`);
    }
    if (parts.length) {
        metaEl.textContent = parts.join('　');
        metaEl.hidden = false;
    } else {
        metaEl.textContent = '';
        metaEl.hidden = true;
    }

    modal.style.display = 'flex';

    const okBtn = document.getElementById('globalErrorOk');
    if (okBtn) {
        setTimeout(() => okBtn.focus(), 0);
    }
}

/**
 * Api 层已对统一错误弹窗时不再重复提示。
 */
function notifyErrorUnlessShown(error, fallbackMessage) {
    if (error && error.apiErrorDialogShown) return;
    const msg = (error && error.message) || fallbackMessage || '操作失败';
    if (typeof showErrorDialog === 'function') {
        showErrorDialog({title: '错误', message: msg});
    } else {
        window.alert(msg);
    }
}
