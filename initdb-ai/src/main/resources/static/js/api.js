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

  async function requestJson(url, body) {
    const response = await fetch(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body || {})
    });
    const parsedBody = await parseResponseBody(response);
    if (!response.ok) {
      throw buildHttpError(response, parsedBody);
    }
    return parsedBody;
  }

  async function requestForm(url, formData) {
    const response = await fetch(url, {
      method: 'POST',
      body: formData
    });
    const parsedBody = await parseResponseBody(response);
    if (!response.ok) {
      throw buildHttpError(response, parsedBody);
    }
    return parsedBody;
  }

  global.Api = {
    requestJson,
    requestForm
  };
})(window);

