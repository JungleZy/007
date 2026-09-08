import {ref, reactive, toRaw} from 'vue';
export default function DeptTreeDo(state) {
  const searchTeam=()=>{
    // console.log(state.searchValue)
  };
  const dataTreeDg=(pt, tree)=> {
    let newRR = [];
    for (let t of tree) {
      if (pt.id === t.parentId) {
        let ct = dataTreeDg(t, tree);
        if (ct.length !== 0) {
          newRR.push({
            title: t.departmentName,
            key: t.id,
            value: t.id,
            id:t.id,
            parentId:t.parentId,
            children: ct
          });
        } else {
          newRR.push({
            title: t.departmentName,
            parentId:t.parentId,
            value: t.id,
            id:t.id,
            key: t.id,
          });
        }
      }
    }
    return newRR;
  };
  return{
    searchTeam,
    dataTreeDg
  }
}