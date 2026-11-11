// Test-only CRA 5 loopback launcher. prepareUrls omits lanUrlForConfig for an
// explicit loopback HOST; proxy-enabled CRA then passes [undefined] to allowedHosts.
// Supply the exact loopback allowlist entry, retaining host validation and binding.
const utilities = require('../../node_modules/react-dev-utils/WebpackDevServerUtils');
const prepare = utilities.prepareUrls;
utilities.prepareUrls = (...args) => ({ ...prepare(...args), lanUrlForConfig: '127.0.0.1' });
