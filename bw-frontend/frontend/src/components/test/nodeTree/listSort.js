const timeValue = (value) => {
   if (value === null || value === undefined || value === '') return 0
   const numeric = Number(value)
   if (Number.isFinite(numeric)) return numeric
   const parsed = Date.parse(String(value).replace(' ', 'T'))
   return Number.isFinite(parsed) ? parsed : 0
}

const listSort=(e)=>{
   return e.sort(function (a,b) {
      const bTime = b['createTime'] ?? b['create_time']
      const aTime = a['createTime'] ?? a['create_time']
      return timeValue(bTime) - timeValue(aTime)
   })
};
export {listSort}