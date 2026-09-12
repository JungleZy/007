export const analyzeChannel = (table, channels = []) => {
    const byIndex = new Map((channels ?? []).map(channel => [Number(channel.indexs), channel]))
    for (const row of table) {
        for (const cell of row) {
            if (cell.isParameter !== '信道') continue
            const receive = cell.xdValues !== undefined
            const channel = byIndex.get(Number(receive ? cell.xdValues : cell.xdValuef))
            cell.params = channel?.[receive ? 'receptionChananel' : 'sendChananel'] ?? null
        }
    }
    return table
}

export const netIP = (table, serialNumbers = []) => {
    for (const row of table) {
        const serial = row.find(cell => cell.isParameter === '序号')
        for (const cell of row) {
            if (cell.isParameter === '网路地址') cell.params = serialNumbers?.[0]?.networkdress ?? null
            if (cell.isParameter === '单台地址') cell.params = serialNumbers?.[Number(serial?.value)]?.dressname ?? null
        }
    }
    return table
}

export const applyScoringDetails = (table, details) => {
    for (const detail of details) {
        const cell = table[detail.xy[0]]?.[detail.xy[1]]
        if (!cell) throw new Error('服务端评分明细与题目坐标不一致')
        cell.params = detail.actual === '' ? null : detail.actual
        cell.correct = detail.correct
    }
}
