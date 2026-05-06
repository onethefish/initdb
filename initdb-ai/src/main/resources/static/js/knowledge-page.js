'use strict';

(function bootKnowledgePage() {
    function run() {
        if (typeof initKnowledgePage !== 'function') {
            return;
        }
        initKnowledgePage();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', run);
    } else {
        run();
    }
})();
