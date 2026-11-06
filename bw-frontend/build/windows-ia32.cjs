const path = require('node:path');

module.exports = {
    extends: null,
    ...require('../package.json').build,
    electronVersion: '22.3.27',
    electronDist: process.env.NIP_ELECTRON22_DIST || path.resolve(__dirname, '../.cache/electron22-ia32'),
    directories: {output: 'out/win7-ia32'},
    extraFiles: [...require('../package.json').build.extraFiles,
        {from: '.cache/ch340-release', to: 'drivers/ch340'}],
    win: {
        ...require('../package.json').build.win,
        artifactName: '${productName}-win7-${version}-ia32.exe',
        target: [{target: 'nsis', arch: ['ia32']}]
    },
    nsis: {
        oneClick: false,
        allowToChangeInstallationDirectory: true,
        perMachine: false,
        deleteAppDataOnUninstall: false
    }
};
