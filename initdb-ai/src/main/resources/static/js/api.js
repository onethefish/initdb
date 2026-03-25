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
    try {
      return JSON.parse(text);
    } catch (e) {
      return text;
    }
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

  async function requestJson(url, body) {
    const response = await fetch(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body || {})
    });
    const parsedBody = await parseResponseBody(response);
    return unwrapResponse(response, parsedBody);
  }

  async function requestForm(url, formData) {
    const response = await fetch(url, {
      method: 'POST',
      body: formData
    });
    const parsedBody = await parseResponseBody(response);
    return unwrapResponse(response, parsedBody);
  }

  global.Api = {
    requestJson,
    requestForm
  };
})(window);

