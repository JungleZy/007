const architectureAliases = {
    x64: 'x64', amd64: 'x64', x86_64: 'x64',
    arm64: 'arm64', aarch64: 'arm64',
    loong64: 'loong64', loongarch64: 'loong64',
    mips64el: 'mips64el', mips64le: 'mips64el',
    sw64: 'sw64', riscv64: 'riscv64'
};

const linuxArchitectures = ['x64', 'arm64', 'loong64', 'mips64el', 'sw64', 'riscv64'];

function mysqlTarget(platform, architecture) {
    const os = platform === 'win32' || platform === 'windows' ? 'windows' : platform;
    const arch = architectureAliases[String(architecture).toLowerCase()];
    if ((os === 'windows' && arch === 'x64') || (os === 'linux' && linuxArchitectures.includes(arch))) {
        return `${os}-${arch}`;
    }
    throw new Error(`Unsupported MySQL tools target: ${platform}/${architecture}`);
}

function parseTarget(target) {
    const [platform, architecture, extra] = target.split('-');
    if (extra || mysqlTarget(platform, architecture) !== target) throw new Error(`Invalid MySQL tools target: ${target}`);
    return {platform, architecture};
}

module.exports = {mysqlTarget, parseTarget, linuxArchitectures};
