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
