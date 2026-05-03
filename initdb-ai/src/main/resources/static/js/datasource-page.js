'use strict';

(function bootDatasourcePage() {
    function run() {
        if (!document.getElementById('datasourceTableBody')) {
            return;
        }
        if (typeof queryDatasourcePage !== 'function') {
            return;
        }
        queryDatasourcePage();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', run);
    } else {
        run();
    }
})();
