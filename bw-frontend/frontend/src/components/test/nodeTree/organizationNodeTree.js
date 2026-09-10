//组织知识节点树
const treeOrganizeSb = (tree, newTree) => {
   for (let r of tree) {
      if (r.parentId === '1') {
         let ct = dataTreeDg(r, tree);
         if (ct.length !== 0) {
            newTree.push({
               title: r.name,
               key: r.id,
               value: r.id,
               parentId: r.parentId,
               isOpen: true,
               children: dataTreeDg(r, tree),
            });
         } else {
            newTree.push({
               title: r.name,
               key: r.id,
               value: r.id,
               parentId: r.parentId,
            })
         }
      }
   }
   return newTree;
};
const dataTreeDg = (pt, tree) => {
   let newRR = [];
   for (let t of tree) {
      if (pt.id === t.parentId) {
         let ct = dataTreeDg(t, tree);
         if (ct.length !== 0) {
            newRR.push({
               title: t.name,
               key: t.id,
               value: t.id,
               parentId: t.parentId,
               children: ct,
               type: 'childNode'
            });
         } else {
            newRR.push({
               title: t.name,
               key: t.id,
               value: t.id,
               parentId: t.parentId,
               type: 'childNode'
            });
         }
      }
   }
   return newRR;
};

export {treeOrganizeSb}