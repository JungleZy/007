package com.nip.service;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * @description:
 * @author: zc
 * @create: 2023-08-03 16:29
 */
@ApplicationScoped
public class BaseService {

//    @Inject
//    SessionFactory sessionFactory;
//
//    /**
//     * sql 需要执行的sql
//     * clazz 返回的class类
//     * paraMap 条件查询需要的参数
//     */
//    public <R> List<R> queryListBySql(String sql, Class<R> clazz, Map<String, Object> paraMap) {
//        //try语句中获取的资源在try代码块执行完后资源会自动关闭
//        try (Session session = sessionFactory.openSession()) {
//            Query sqlQuery = session.unwrap(Session.class)
//                    .createNativeQuery(sql)
//                    .setResultTransformer(Transformers.aliasToBean(clazz));
//            if (ObjectUtil.isNotEmpty(paraMap)) {
//                Set<Map.Entry<String, Object>> entrySet = paraMap.entrySet();
//                for (Map.Entry<String, Object> entry : entrySet) {
//                    sqlQuery.setParameter(entry.getKey(), entry.getValue());
//                }
//            }
//            return sqlQuery.list();
//        }
//    }
}
