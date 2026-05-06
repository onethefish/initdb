'use strict';

(function bootDatasourcePage() {
    async function run() {
        if (!document.getElementById('datasourceTableBody')) {
            return;
        }
        if (typeof queryDatasourcePage !== 'function') {
            return;
        }
        await queryDatasourcePage();
        const kb = (new URLSearchParams(window.location.search).get('kb') || '').trim();
        if (kb && typeof window.enterEmbeddedKnowledge === 'function') {
            await window.enterEmbeddedKnowledge(kb);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', run);
    } else {
        run();
    }
})();
