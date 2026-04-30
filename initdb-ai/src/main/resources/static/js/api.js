// 统一封装前端请求（避免散落多个 fetch）
(function (global) {
    'use strict';

    async function parseResponseBody(response) {
        const contentType = (response.headers && response.headers.get && response.headers.get('content-type')) || '';
        if (contentType.includes('application/json')) {
            return await response.json();
        }
        // 兜底：尝试 json，否则返回文本
        const text = await response.text();
        if (!text) return {};
        try {
            return JSON.parse(text);
        } catch (e) {
            return text;
        }
    }

    function buildUrlWithParams(url, params) {
        if (!params || typeof params !== 'object' || Object.keys(params).length === 0) return url;
        const usp = new URLSearchParams();
        Object.keys(params).forEach(key => {
            const value = params[key];
            if (value === undefined || value === null) return;
            usp.append(key, String(value));
        });
        const qs = usp.toString();
        if (!qs) return url;
        return url.includes('?') ? `${url}&${qs}` : `${url}?${qs}`;
    }

    function buildHttpError(response, body) {
        let details = '';
        if (typeof body === 'string') {
            details = body.slice(0, 1000);
        } else if (body && typeof body === 'object') {
            details = JSON.stringify(body).slice(0, 1000);
        }
        return new Error(`HTTP ${response.status} ${response.statusText}${details ? ` - ${details}` : ''}`);
    }

    function isUnifiedResponse(body) {
        return !!body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code');
    }

    function normalizeUnifiedResponse(body) {
        const code = body.code;
        const ok = String(code) === '200';
        const message = body.message || '';
        const traceId = body.traceId || '';
        return {ok, code, message, traceId, data: body.data};
    }

    function buildBusinessError(unified) {
        const base = unified.message || `业务请求失败(code: ${unified.code})`;
        const traceSuffix = unified.traceId ? ` [traceId: ${unified.traceId}]` : '';
        return new Error(`${base}${traceSuffix}`);
    }

    function unwrapResponse(response, parsedBody) {
        if (isUnifiedResponse(parsedBody)) {
            const unified = normalizeUnifiedResponse(parsedBody);
            if (!unified.ok) {
                throw buildBusinessError(unified);
            }
            return unified.data;
        }

        if (!response.ok) {
            throw buildHttpError(response, parsedBody);
        }

        return parsedBody;
    }

    async function requestJsonMethod(method, url, options) {
        const opts = options || {};
        const params = opts.params;
        const body = opts.body;
        const headers = opts.headers || {};

        const finalUrl = buildUrlWithParams(url, params);
        const fetchOptions = {
            method: String(method || 'POST').toUpperCase(),
            headers: {...headers}
        };

        // GET 通常不带 body（但如果调用方传了 body，这里也会按 method!==GET 处理）
        if (fetchOptions.method !== 'GET' && body !== undefined) {
            fetchOptions.headers['Content-Type'] = 'application/json';
            fetchOptions.body = JSON.stringify(body);
        }

        const response = await fetch(finalUrl, fetchOptions);
        const parsedBody = await parseResponseBody(response);
        return unwrapResponse(response, parsedBody);
    }

    async function requestFormMethod(method, url, formData, options) {
        const opts = options || {};
        const params = opts.params;
        const finalUrl = buildUrlWithParams(url, params);

        const fetchOptions = {
            method: String(method || 'POST').toUpperCase(),
            body: formData
        };

        const response = await fetch(finalUrl, fetchOptions);
        const parsedBody = await parseResponseBody(response);
        return unwrapResponse(response, parsedBody);
    }

    async function requestForm(url, formData) {
        return requestFormMethod('POST', url, formData);
    }

    // 便利方法（JSON）
    function get(url, params) {
        return requestJsonMethod('GET', url, {params});
    }

    function post(url, body) {
        return requestJsonMethod('POST', url, {body});
    }

    function put(url, body) {
        return requestJsonMethod('PUT', url, {body});
    }

    function del(url, options) {
        const opts = options || {};
        return requestJsonMethod('DELETE', url, {params: opts.params, body: opts.body});
    }

    global.Api = {
        requestForm,
        get,
        post,
        put,
        del
    };
})(window);

