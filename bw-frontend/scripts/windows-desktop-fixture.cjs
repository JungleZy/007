'use strict';
// Private native-MySQL fixture only. Never pipe the source snapshot into mysql.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const {spawnSync} = require('node:child_process');
const assert = require('node:assert/strict');

const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
function createFixture({repo, mysql, port, schema, output}) {
  assert.match(schema, /^rust_desktop_[a-f0-9]+$/);
  function sql(statement, database = schema) {
    // A real batch-file handle avoids Windows mysql's short stdin-pipe/EOF
    // ambiguity. Keep each UTF-8 command newline-terminated; never accept empty
    // COUNT results as zero. The private SQL file is not an evidence artifact.
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-desktop-sql-'));
    const filename = path.join(directory, 'statement.sql');
    let descriptor;
    try {
      fs.writeFileSync(filename, `${statement}\n`, {encoding: 'utf8', mode: 0o600});
      descriptor = fs.openSync(filename, 'r');
      const result = spawnSync(mysql, ['--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
        `--port=${port}`, '--user=root', '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
        ...(database ? [database] : [])], {
        stdio: [descriptor, 'pipe', 'pipe'], encoding: 'utf8', timeout: 180000, maxBuffer: 32 * 1024 * 1024,
        env: {...process.env, MYSQL_PWD: ''}, windowsHide: true
      });
      assert.equal(result.status, 0, `Private MySQL command failed: ${result.error || result.stderr}`);
      return result.stdout.trim();
    } finally {
      if (descriptor !== undefined) fs.closeSync(descriptor);
      fs.rmSync(directory, {recursive: true, force: true});
    }
  }
  sql(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`, null);
  const snapshot = fs.readFileSync(path.join(repo, 'backend/database/project006.sql'), 'utf8');
  const tables = [];
  let block = null;
  // Navicat's canonical snapshot uses one column/index per line and a standalone
  // closing ENGINE line. This allowlist refuses unexpected SQL inside a DDL block.
  for (const line of snapshot.split(/\r?\n/)) {
    if (/^CREATE TABLE `[A-Za-z0-9_]+`\s+\($/.test(line)) {
      assert.equal(block, null, 'Nested CREATE TABLE in snapshot');
      block = [line];
    } else if (block) {
      assert(!/^\s*(?:INSERT|REPLACE|UPDATE|DELETE|DROP|USE|DELIMITER|CREATE|\/\*!)/i.test(line), 'Non-DDL inside CREATE TABLE');
      block.push(line);
      if (/^\) ENGINE\s*=.*;\s*$/.test(line)) {
        const ddl = block.join('\n');
        assert.equal((ddl.match(/;/g) || []).length, 1, 'Unexpected SQL delimiter in DDL');
        tables.push(ddl.replace(/AUTO_INCREMENT\s*=\s*\d+/g, 'AUTO_INCREMENT=1'));
        block = null;
      }
    }
  }
  assert.equal(block, null, 'Unterminated CREATE TABLE');
  assert(tables.length >= 100, 'Canonical schema unexpectedly incomplete');
  assert.equal(tables.length, (snapshot.match(/^CREATE TABLE /gm) || []).length, 'Unrecognized source CREATE TABLE');
  const ddl = 'SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n' + tables.join('\n') + '\nSET FOREIGN_KEY_CHECKS=1;\n';
  fs.writeFileSync(path.join(output, 'schema-only.sql'), ddl);
  sql(ddl);
  const migrationDir = path.join(repo, 'backend/database/migrations');
  const rehearsal = fs.readFileSync(path.join(repo, 'backend/scripts/rehearse-migrations.sh'), 'utf8');
  const declared = [...rehearsal.matchAll(/\$PROJECT_ROOT\/database\/migrations\/([^"\s]+\.sql)/g)].map(match => match[1]);
  const discovered = fs.readdirSync(migrationDir).filter(name => name.endsWith('.sql')).sort();
  assert.deepEqual(declared, discovered, 'Canonical migration declaration/order drift');
  const migrations = [];
  for (const name of declared) {
    const content = fs.readFileSync(path.join(migrationDir, name), 'utf8');
    sql(content);
    migrations.push({name, sha256: sha256(content)});
  }
  const names = sql('SHOW TABLES;').split(/\r?\n/);
  for (const name of names) {
    assert.match(name, /^[A-Za-z0-9_]+$/);
    assert.equal(sql(`SELECT COUNT(*) FROM \`${name}\`;`), '0', `Unexpected pre-fixture DATA in ${name}`);
  }
  // Exactly the synthetic role/menu definitions used by the prior real Linux client.
  const evidencePath = path.join(repo, 'docs/reviews/rust-migration/phase9-frontend-smoke.json');
  const evidence = JSON.parse(fs.readFileSync(evidencePath, 'utf8'));
  const quote = value => `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
  sql("INSERT INTO t_role(id,title,is_admin,is_default) VALUES ('ui-admin-role','验收教员',0,0);");
  for (const [index, menu] of evidence.fixture.menus.entries()) {
    const [id, parent, route, name, title, component, isMenu] = menu;
    sql('INSERT INTO t_menus(id,parent_id,`key`,path,name,title,component,is_menu,is_bread,sort) VALUES (' +
      [id, parent, route, route, name, title, component].map(quote).join(',') + `,${Number(isMenu)},0,${index});`);
    sql(`INSERT INTO t_role_menus(id,role_id,menu_id,per) VALUES (${quote(`ui-per-${index}`)},'ui-admin-role',${quote(id)},'[]');`);
  }
  const report = {schema, host: '127.0.0.1', port, sourceDataImported: false,
    canonicalSnapshotSha256: sha256(snapshot), schemaOnlySha256: sha256(ddl), sourceTables: tables.length,
    migratedTables: names.length, allTablesEmptyBeforeSyntheticSeed: true, migrations,
    fixtureSource: 'docs/reviews/rust-migration/phase9-frontend-smoke.json', fixtureSourceSha256: sha256(fs.readFileSync(evidencePath)),
    role: {id: 'ui-admin-role', is_admin: 0, is_default: 0}, menus: evidence.fixture.menus,
    accountCreation: 'Real /api/user/signin; credentials generated in memory; login via renderer form'};
  fs.writeFileSync(path.join(output, 'database-fixture.json'), JSON.stringify(report, null, 2));
  return {sql, report};
}
module.exports = {createFixture};
