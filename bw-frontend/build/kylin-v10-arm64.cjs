const path = require('node:path');
const base = require('../package.json').build;

module.exports = {
    ...base,
    extends: null,
    electronVersion: '30.5.1',
    electronDist: process.env.NIP_KYLIN_ELECTRON_DIR || path.resolve(__dirname, '../.cache/kylin-arm64-components/electron'),
    directories: {output: 'out/kylin-v10-arm64'},
    linux: {
        ...base.linux,
        executableName: 'nip-traffic-system',
        icon: 'build/icons/512x512.png',
        target: [{target: 'dir', arch: ['arm64']}]
    }
};
