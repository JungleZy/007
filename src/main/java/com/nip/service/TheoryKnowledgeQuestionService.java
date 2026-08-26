package com.nip.service;


import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TheoryKnowledgeQuestionDao;
import com.nip.dao.TheoryKnowledgeQuestionLevelDao;
import com.nip.dao.UserDao;
import com.nip.dto.TheoryKnowledgeQuestionAllDto;
import com.nip.dto.TheoryKnowledgeQuestionDto;
import com.nip.dto.TheoryKnowledgeQuestionLevelDto;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.ID;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/19 14:23
 */
@ApplicationScoped
public class TheoryKnowledgeQuestionService {
  private final TheoryKnowledgeQuestionDao theoryKnowledgeQuestionDao;
  private final UserService userService;
  private final TheoryKnowledgeQuestionLevelDao theoryKnowledgeQuestionLevelDao;
  private final UserDao userDao;

  @Inject
  public TheoryKnowledgeQuestionService(TheoryKnowledgeQuestionDao theoryKnowledgeQuestionDao,
                                        UserService userService,
                                        TheoryKnowledgeQuestionLevelDao theoryKnowledgeQuestionLevelDao,
                                        UserDao userDao) {
    this.theoryKnowledgeQuestionDao = theoryKnowledgeQuestionDao;
    this.userService = userService;
    this.theoryKnowledgeQuestionLevelDao = theoryKnowledgeQuestionLevelDao;
    this.userDao = userDao;
  }

  @Transactional
  public Response<TheoryKnowledgeQuestionEntity> saveTheoryKnowledgeQuestion(String token, TheoryKnowledgeQuestionDto questionDto) {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeQuestionEntity entity = new TheoryKnowledgeQuestionEntity();
    entity.setTopic(questionDto.getTopic());
    entity.setCreateUserId(userEntity.getId());
    entity.setAnalysis(questionDto.getAnalysis());
    entity.setType(questionDto.getType());
    entity.setAnswer(questionDto.getAnswer());
    entity.setLevelId(questionDto.getLevelId());
    entity.setOptions(questionDto.getOptions());
    if (!StringUtils.isEmpty(questionDto.getId())) {
      TheoryKnowledgeQuestionEntity tkq = theoryKnowledgeQuestionDao.findByIdOptional(questionDto.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到该试题"));
      tkq.setTopic(questionDto.getTopic());
      tkq.setCreateUserId(userEntity.getId());
      tkq.setAnalysis(questionDto.getAnalysis());
      tkq.setType(questionDto.getType());
      tkq.setAnswer(questionDto.getAnswer());
      tkq.setLevelId(questionDto.getLevelId());
      tkq.setOptions(questionDto.getOptions());
      return ResponseResult.success(tkq);
    } else {
      TheoryKnowledgeQuestionEntity save = theoryKnowledgeQuestionDao.save(entity);
      return ResponseResult.success(save);
    }
  }

  @Transactional
  public Response<TheoryKnowledgeQuestionLevelEntity> saveTheoryKnowledgeQuestionLevel(String token, TheoryKnowledgeQuestionLevelDto map) {
    TheoryKnowledgeQuestionLevelEntity entity = new TheoryKnowledgeQuestionLevelEntity();
    UserEntity userEntity = userService.getUserByToken(token);
    entity.setParentId(map.getParentId());
    entity.setName(map.getName());
    entity.setCreateUserId(userEntity.getId());
    if (!StringUtils.isEmpty(map.getId())) {
      TheoryKnowledgeQuestionLevelEntity tkql = theoryKnowledgeQuestionLevelDao.findByIdOptional(map.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到该题目分类"));
      tkql.setParentId(map.getParentId());
      tkql.setName(map.getName());
      return ResponseResult.success(tkql);
    } else {
      TheoryKnowledgeQuestionLevelEntity save = theoryKnowledgeQuestionLevelDao.save(entity);
      return ResponseResult.success(save);
    }
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionLevelEntity>> findAllTheoryKnowledgeQuestionLevel() {
    List<TheoryKnowledgeQuestionLevelEntity> res = theoryKnowledgeQuestionLevelDao.findAll(Sort.by("createTime").descending()).list();
    return ResponseResult.success(res);
  }

  List<String> ids = new ArrayList<>();

  private void findAllLevel(String id) {
    ids.add(id);
    List<TheoryKnowledgeQuestionLevelEntity> allByParentId = theoryKnowledgeQuestionLevelDao.findAllByParentId(id);
    if (!allByParentId.isEmpty()) {
      allByParentId.forEach(a -> {
        findAllLevel(a.getId());
      });
    }
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionAllDto>> findAllQuestionByLevelId(String id, String type, String name) {
    findAllLevel(id);
    List<TheoryKnowledgeQuestionEntity> allByIdIn;
    if (!StringUtils.isEmpty(type)) {
      if (StringUtils.isEmpty(name)) {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdInAndType(ids, Integer.parseInt(type));
      } else {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdInAndTypeAndTopicLike(ids, Integer.parseInt(type), "%" + name + "%");
      }
    } else {
      if (StringUtils.isEmpty(name)) {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdIn(ids);
      } else {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdInAndTopicLike(ids, "%" + name + "%");
      }
    }
    ids = new ArrayList<>();
    List<TheoryKnowledgeQuestionAllDto> theoryKnowledgeQuestionAllDtos = PojoUtils.convert(allByIdIn, TheoryKnowledgeQuestionAllDto.class);

    List<UserEntity> userList = userDao.findAll().list();
    Map<String, List<UserEntity>> userMap = userList.stream().collect(Collectors.groupingBy(UserEntity::getId));

    theoryKnowledgeQuestionAllDtos.forEach(ques -> {
      List<UserEntity> userEntities = userMap.get(ques.getCreateUserId());
      if (ObjectUtil.isNotEmpty(userEntities)) {
        ques.setCreateUserName(userEntities.getFirst().getUserName());
      }
    });
    return ResponseResult.success(theoryKnowledgeQuestionAllDtos);
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionLevelEntity>> deleteTheoryKnowledgeQuestionLevelById(String id) {
    theoryKnowledgeQuestionLevelDao.deleteById(id);
    return ResponseResult.success(theoryKnowledgeQuestionLevelDao.findAll().list());
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionEntity>> deleteTheoryKnowledgeQuestion(String id) {
    theoryKnowledgeQuestionDao.deleteById(id);
    return ResponseResult.success(theoryKnowledgeQuestionDao.findAll().list());
  }

//    /**
//     * 上传题库
//     */
//    public String upLoadFile(UploadDto uploadDto) {
//        InputStream is = null;
//        InputStreamReader isr = null;
//        BufferedReader bufferedReader = null;
//        StringBuilder ret = new StringBuilder();
//        HWPFDocument document = null;
//        XWPFDocument doc = null;
//        try {
//            is = uploadDto.getFile().getBody(InputStream.class, null);
//            String fileName = uploadDto.getFile().getFileName();
//            //如果文件不是txt或docx 则抛出异常
//            if (!fileName.endsWith(".TXT") && !fileName.endsWith(".txt") &&
//                    !fileName.endsWith(".doc") && !fileName.endsWith(".docx")) {
//                throw new RuntimeException("只支持txt、doc、docx格式文件");
//            }
//
//            if (fileName.endsWith(".TXT") || fileName.endsWith(".txt")) {
//                //得到字符集
//                InputStream fileEncodeStream = uploadDto.getFile().getBody(InputStream.class, null);
//                String fileEncode = CharsetUtils.getFileEncode(fileEncodeStream);
//                isr = new InputStreamReader(is, fileEncode);
//                bufferedReader = new BufferedReader(isr);
//                String strItem;
//                while ((strItem = bufferedReader.readLine()) != null) {
//                    ret.append(strItem + "\n");
//                }
//
//            } else {
//                if (fileName.endsWith(".doc")) {
//                    document = new HWPFDocument(is);
//                    String documentText = document.getDocumentText();
//                    ret.append(documentText);
//                } else if (fileName.endsWith(".docx")) {
//                    doc = new XWPFDocument(is);
//                    List<XWPFParagraph> paragraphs = doc.getParagraphs();
//                    for (XWPFParagraph paragraph : paragraphs) {
//                        String text = paragraph.getText() + System.getProperty("line.separator");
//                        ret.append(text);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e.getMessage());
//        } finally {
//            if (bufferedReader != null) {
//                try {
//                    bufferedReader.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (isr != null) {
//                try {
//                    isr.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (is != null) {
//                try {
//                    is.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (document != null) {
//                try {
//                    document.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (doc != null) {
//                try {
//                    doc.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//
//        return ret.toString();
//    }

  /**
   * 导出模板
   */
  public void exportTemplate(HttpServerResponse response) {
//        response.putHeader("content-type", "application/octet-stream;charset=UTF-8");
////        response.setContentType("application/octet-stream");
////        response.setCharset(Charset.forName("UTF-8"));
//        response.putHeader("Content-Disposition", "attachment; filename=模板.docx");
//        ServletOutputStream outputStream = null;
//        InputStream inputStream = null;
//        try {
//            outputStream = response.getOutputStream();
//            //读取模板内容
//            inputStream = this.getClass().getResourceAsStream("/template/docxTemplate.docx");
//            byte[] bytes = new byte[1024];
//            int line = 0;
//            while ((line = inputStream.read(bytes)) != -1) {
//                outputStream.write(bytes, 0, line);
//                outputStream.flush();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if (inputStream != null) {
//                try {
//                    inputStream.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }

  }

  public List<TheoryKnowledgeQuestionEntity> exportQuestionByLevelId(HttpServerResponse response, String levelId) {
    return theoryKnowledgeQuestionDao.findAllByLevelId(levelId);
  }
}
