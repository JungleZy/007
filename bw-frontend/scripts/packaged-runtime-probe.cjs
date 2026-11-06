const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const Module = require('node:module');

const archive = path.resolve(process.argv[2]);
const load = Module.createRequire(path.join(archive, 'package.json'));
const resolveFilename = Module._resolveFilename;
// 即使在开发目录内测试，也禁止从开发机 node_modules 借用缺失的包。
Module._resolveFilename = function (request, ...args) {
    const resolved = resolveFilename.call(this, request, ...args);
    if (!Module.isBuiltin(request) && !resolved.startsWith(archive + path.sep)) {
        throw new Error(`Packaged dependency resolves outside app.asar: ${request} -> ${resolved}`);
    }
    return resolved;
};

(async () => {
    const metadata = load('./package.json');
    for (const dependency of Object.keys(metadata.dependencies || {})) load(dependency);
    load('./electron/core/index.js');
    load('./electron/service/integrated-runtime.js');
    load('./electron/service/http/node_server.js');
    const Datastore = load('nedb-promises');
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-packaged-runtime-'));
    try {
        const filename = path.join(directory, 'config.db');
        const database = Datastore.create({filename, autoload: true});
        await database.insert({_id: 'runtime-check', value: 1});
        await database.update({_id: 'runtime-check'}, {$set: {value: 42}});
        const reopened = Datastore.create({filename, autoload: true});
        if ((await reopened.findOne({_id: 'runtime-check'}))?.value !== 42) throw new Error('Packaged NeDB persistence check failed');
    } finally {
        fs.rmSync(directory, {recursive: true, force: true});
    }
    console.log('PACKAGED_RUNTIME_OK');
})().catch(error => { console.error(error); process.exitCode = 1; });
