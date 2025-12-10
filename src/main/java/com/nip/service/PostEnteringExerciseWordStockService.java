package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostEnteringExerciseWordStockTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostEnteringExerciseWordStockDao;
import com.nip.dao.UserDao;
import com.nip.dto.PostEnteringExerciseWordStockDto;
import com.nip.entity.PostEnteringExerciseWordStockEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 14:03
 * @Description:
 */
@ApplicationScoped
public class PostEnteringExerciseWordStockService {

  private final PostEnteringExerciseWordStockDao enteringExerciseWordStockDao;
  private final UserDao userDao;

  @Inject
  public PostEnteringExerciseWordStockService(PostEnteringExerciseWordStockDao enteringExerciseWordStockDao,
                                              UserDao userDao) {
    this.enteringExerciseWordStockDao = enteringExerciseWordStockDao;
    this.userDao = userDao;
  }

  public PostEnteringExerciseWordStockDto findByType(Integer type) {
    PostEnteringExerciseWordStockEntity entity = enteringExerciseWordStockDao.findByType(type);
    if (entity == null) {
      return new PostEnteringExerciseWordStockDto();
    }
    return PojoUtils.convertOne(entity, PostEnteringExerciseWordStockDto.class);
  }

  public PostEnteringExerciseWordStockDto view(FileUpload file) {
    //    StringBuilder ret = new StringBuilder();
    //    String fileName = file.getOriginalFilename();
    //
    //
    //    if( fileName == null || ((!fileName.endsWith(".doc")) && (!fileName.endsWith(".docx")) && (!fileName.endsWith("txt")) && (!fileName.endsWith("TXT")))){
    //      throw  new RuntimeException("只支持.doc .docx的word文档或.txt文本");
    //    }
    //    InputStream is = null;
    //    HWPFDocument document = null;
    //    XWPFDocument doc = null;
    //    Reader reader = null;
    //    BufferedReader bufferedReader = null;
    //    try {
    //      is = file.getInputStream();
    //      if(fileName.endsWith(".doc")){
    //        document = new HWPFDocument(is);
    //        String documentText = document.getDocumentText();
    //        ret.append(documentText);
    //      }else if(fileName.endsWith(".docx")){
    //        doc = new XWPFDocument(is);
    //        List<XWPFParagraph> paragraphs = doc.getParagraphs();
    //        for (XWPFParagraph paragraph : paragraphs) {
    //          String text = paragraph.getText()+System.getProperty("line.separator");
    //          ret.append(text);
    //        }
    //      }else {
    //        //得到字符集
    //        InputStream fileEncodeStream = file.getInputStream();
    //        String fileEncode = CharsetUtils.getFileEncode(fileEncodeStream);
    //
    //        reader = new InputStreamReader(is,fileEncode);
    //        bufferedReader = new BufferedReader(reader);
    //        String strItem;
    //        while ((strItem=bufferedReader.readLine())!=null){
    //          ret.append(strItem+"\n");
    //        }
    //      }
    //
    //    } catch (IOException e) {
    //      e.printStackTrace();
    //    }
    //    finally {
    //      if(is != null){
    //        try {
    //          is.close();
    //        } catch (IOException e) {
    //          e.printStackTrace();
    //        }
    //      }
    //
    //      if(document != null){
    //        try {
    //          document.close();
    //        } catch (IOException e) {
    //          e.printStackTrace();
    //        }
    //      }
    //
    //      if(doc != null){
    //        try {
    //          doc.close();
    //        } catch (IOException e) {
    //          e.printStackTrace();
    //        }
    //      }
    //      if(bufferedReader!=null){
    //        try {
    //          bufferedReader.close();
    //        } catch (IOException e) {
    //          e.printStackTrace();
    //        }
    //      }
    //      if (reader!=null){
    //        try {
    //          reader.close();
    //        } catch (IOException e) {
    //          e.printStackTrace();
    //        }
    //      }
    //    }
//    PostEnteringExerciseWordStockDto vo = new PostEnteringExerciseWordStockDto();
    //    vo.setContent(ret.toString());
    //    vo.setName(fileName.substring(0,fileName.lastIndexOf(".")));

    return new PostEnteringExerciseWordStockDto();
  }

  @Transactional(rollbackOn = Exception.class)
  public PostEnteringExerciseWordStockDto add(PostEnteringExerciseWordStockDto vo, String token) throws Exception {
    //从token中获取用户
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    if (!Objects.isNull(vo.getId())) {
      PostEnteringExerciseWordStockEntity wordStockEntity = enteringExerciseWordStockDao.findById(vo.getId());
      wordStockEntity.setName(vo.getName());
      wordStockEntity.setType(vo.getType());
      String[] split = vo.getContent().replaceAll("[\\r\\n]+", "##").split("##");
      List<String> collect = Arrays.stream(split).filter(el -> !el.isEmpty()).toList();
      wordStockEntity.setContent(JSONUtils.toJson(collect));
      return PojoUtils.convertOne(wordStockEntity, PostEnteringExerciseWordStockDto.class);
    }
    //如果用户id为空，则需要校验数据库中同一用户是否存在同一类型的记录
    PostEnteringExerciseWordStockEntity entity = PojoUtils.convertOne(
        vo, PostEnteringExerciseWordStockEntity.class, (t, e) -> {
          String[] split = vo.getContent().replaceAll("[\\r\\n]+", "@").split("@");
          List<String> collect = Arrays.stream(split).filter(el -> !el.isEmpty()).toList();
          e.setContent(JSONUtils.toJson(collect));
          //确定文章的字数或单词组数
          String content = vo.getContent();
          int wordSize;
          if (e.getType().compareTo(PostEnteringExerciseWordStockTypeEnum.ENGLISHT_WORD.getType()) == 0) {
            wordSize = content.split(" ").length;
          } else {
            wordSize = content.trim().length();
          }
          e.setWordSize(wordSize);
          e.setCreateUserId(userEntity.getId());
          e.setCreateTime(LocalDateTime.now());
        });
    PostEnteringExerciseWordStockEntity save = enteringExerciseWordStockDao.save(entity);
    return PojoUtils.convertOne(save, PostEnteringExerciseWordStockDto.class);
  }

  public List<PostEnteringExerciseWordStockDto> listPage(String token, Integer type) {
    //从token中获取用户
    String sql;
    List<PostEnteringExerciseWordStockEntity> entityList;
    if (type != null) {
      sql = "type = ?1 order by createTime desc";
      entityList = enteringExerciseWordStockDao.find(sql, type).list();
    } else {
      List<Integer> list = Arrays.asList(PostEnteringExerciseWordStockTypeEnum.WUBI_WORD.getType(),
          PostEnteringExerciseWordStockTypeEnum.PY_WORD.getType(),
          PostEnteringExerciseWordStockTypeEnum.ENGLISHT_WORD.getType()
      );
      sql = "type in (?1) order by createTime desc";
      entityList = enteringExerciseWordStockDao.find(sql, list).list();
    }
    return PojoUtils.convert(entityList, PostEnteringExerciseWordStockDto.class, (e, v) -> {
      //兼容之前未统计的字数
      if (e.getWordSize() == null) {
        String content = e.getContent();
        List<String> strings = JSONUtils.fromJson(content, new TypeToken<>() {
        });
        StringBuilder builder = new StringBuilder();
        strings.forEach(builder::append);
        int wordSize;
        if (e.getType().compareTo(PostEnteringExerciseWordStockTypeEnum.ENGLISHT_WORD.getType()) == 0) {
          wordSize = builder.toString().split(" ").length;
        } else {
          wordSize = builder.toString().trim().length();
        }
        e.setWordSize(wordSize);
        enteringExerciseWordStockDao.save(e);
      }
    });
  }

  @Transactional(rollbackOn = Exception.class)
  public void delete(Integer id) {
    enteringExerciseWordStockDao.deleteById(id);
  }

}
