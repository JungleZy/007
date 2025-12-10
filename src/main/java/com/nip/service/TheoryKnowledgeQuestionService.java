package com.nip.service;


import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
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
      TheoryKnowledgeQuestionEntity tkq = theoryKnowledgeQuestionDao.findById(questionDto.getId());
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
      TheoryKnowledgeQuestionLevelEntity tkql = theoryKnowledgeQuestionLevelDao.findById(map.getId());
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
    List<TheoryKnowledgeQuestionAllDto> theoryKnowledgeQuestionAllDtos = JSONUtils.fromJson(JSONUtils.toJson(allByIdIn), new TypeToken<>() {
    });

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

//    /**
//     * 导出指定题库
//     *
//     * @param response
//     * @param levelId
//     */
//    public void exportQuestionByLevelId(HttpServerResponse response, String levelId) {
//        response.putHeader("content-type", "application/octet-stream;charset=UTF-8");
////        response.setContentType("application/octet-stream");
////        response.setCharacterEncoding("UTF-8");
//        response.putHeader("Content-Disposition", "attachment;");
//        //先根据leveId查询出所有题
//        List<TheoryKnowledgeQuestionEntity> questionList = theoryKnowledgeQuestionDao.findAllByLevelId(levelId);
//        Map<Integer, List<TheoryKnowledgeQuestionEntity>> questionTypeList = questionList.stream()
//                .collect(Collectors.groupingBy(TheoryKnowledgeQuestionEntity::getType));
//        XWPFDocument document = new XWPFDocument();
//        //将数组 转成word测验类型，1、单选题，2、多选题，3、判断题，4、填空题，5、简答题(未使用)
//        for (int i = 1; i <= 4; i++) {
//            XWPFParagraph paragraph = document.createParagraph();
//            XWPFRun run = paragraph.createRun();
//            run.setFontFamily("宋体");
//            run.setFontSize(14);
//            run.setBold(true);
//            XWPFParagraph content = document.createParagraph();
//            XWPFRun contentRun = content.createRun();
//            contentRun.setFontSize(10);
//            contentRun.setFontFamily("宋体");
//            //拼接题目和选项答案
//            StringBuilder builder = new StringBuilder();
//            List<TheoryKnowledgeQuestionEntity> questionEntities = questionTypeList.get(i);
//            switch (i) {
//                case 1:
//                    //标题
//                    run.setText("一、单项选择题");
//                    //写内容
//         /* for (int j = 0; j < questionEntities.size(); j++) {
//            TheoryKnowledgeQuestionEntity entity = questionEntities.get(j);
//            //题目
//            String topic = (j+1) + "、" + entity.getTopic() + "\r\n";
//            builder.append(topic);
//
//            //选项 [{"value":"0","label":"1、0"},{"value":"1","label":"2、0"},{"value":"2","label":"3、0"},{"value":"3","label":"4、0"}]
//            List<Map> optionMap = JSONUtils.parseArray(entity.getOptions(), Map.class);
//            for (int z = 0; z < optionMap.size(); z++) {
//              Map map = optionMap.get(z);
//              //得到选项编号 A、
//              String optionNumber = (char)(65+z)+"、";
//              //得到选项内容
//              String label = map.get("label").toString()+" ";
//              //拼接字符串
//              builder.append(optionNumber);
//              builder.append(label);
//            }
//
//            //拼接换行符
//            builder.append("\r\n"+"答案：");
//
//
//          }*/
//                    multipleChoiceHandler(builder, questionEntities, i);
//                    break;
//                case 2:
//                    run.setText("二、不定项选择题");
//                    multipleChoiceHandler(builder, questionEntities, i);
//                    break;
//                case 3:
//                    run.setText("三、判断题");
//                    judgeHandler(builder, questionEntities);
//                    break;
//                case 4:
//                    run.setText("四、填空题");
//                    completionHandler(builder, questionEntities);
//                    break;
//                default:
//
//            }
//            contentRun.setText(builder.toString());
//        }
////        try {
////
////            document.write(response.getOutputStream());
////        } catch (IOException e) {
////            e.printStackTrace();
////            throw new RuntimeException("导出出错：" + e.getMessage());
////        }
//        ;
//
//    /*FileOutputStream outputStream = null;
//    try {
//      outputStream = new FileOutputStream("C:\\Users\\Administrator\\Desktop\\test.docx");
//      document.write(outputStream);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }finally {
//      if(outputStream!=null){
//        try {
//          outputStream.close();
//        } catch (IOException e) {
//          e.printStackTrace();
//        }
//      }
//    }*/
//    }


  /**
   * 选择题
   *
   * @param builder          字符串
   * @param questionEntities 所有题目
   * @param type             1 单选 2 多选
   */
  private void multipleChoiceHandler(StringBuilder builder, List<TheoryKnowledgeQuestionEntity> questionEntities, Integer type) {
    for (int j = 0; j < questionEntities.size(); j++) {
      TheoryKnowledgeQuestionEntity entity = questionEntities.get(j);
      //题目
      String topic = (j + 1) + "、" + entity.getTopic() + "\r\n";
      builder.append(topic);

      //选项 [{"value":"0","label":"驱逐舰支队"},{"value":"1","label":"舰艇大队"},{"value":"2","label":"登陆舰支队"},{"value":"3","label":"快艇支队"}]
      List<Map> optionMap = JSONUtils.fromJson(entity.getOptions(), new TypeToken<>() {
      });
      for (int z = 0; z < optionMap.size(); z++) {
        Map map = optionMap.get(z);
        //得到选项编号 A、
        String optionNumber = (char) (65 + z) + "、";
        //得到选项内容
        String label = map.get("label").toString() + " ";
        //拼接字符串
        builder.append(optionNumber);
        builder.append(label);
      }

      //拼接换行符
      builder.append("\r\n" + "答案：");

      //拼接正确答案 1 单选 2 多选
      if (type == 1) {
        builder.append((char) (Integer.parseInt(entity.getAnswer().replaceAll("\"", "")) + 65)).append("\n\r");
      } else {
        //["0","2","3"]
        List<String> answers = JSONUtils.fromJson(entity.getAnswer(), new TypeToken<>() {
        });
        answers.stream()
            .map(Integer::valueOf)
            .map(item -> (char) (item + 65) + "")
            .forEach(builder::append);
        builder.append("\r\n");
      }
    }
  }

  /**
   * 判断题
   *
   * @param builder          字符串
   * @param questionEntities 所有题目
   */
  private void judgeHandler(StringBuilder builder, List<TheoryKnowledgeQuestionEntity> questionEntities) {
    for (int i = 0; i < questionEntities.size(); i++) {
      TheoryKnowledgeQuestionEntity entity = questionEntities.get(i);
      //题目
      String topic = (i + 1) + "、" + entity.getTopic() + "\r\n";
      builder.append(topic);

      //选项 [{"id":"1","name":"对"},{"id":"2","name":"错"}]
      List<Map<String, Object>> optionMap = JSONUtils.fromJson(entity.getOptions(), new TypeToken<>() {
      });

      //通过答案id 找到答案
      String answerId = entity.getAnswer().replaceAll("\"", "");
      builder.append("答案：");
      optionMap.forEach(map -> {
        String optionNumber = map.get(ID).toString();
        if (Objects.equals(optionNumber, answerId)) {
          String answer = map.get("name").toString();
          builder.append(answer);
        }
      });

      builder.append("\n\r");
    }
  }

  /**
   * 填空题处理
   *
   * @param builder          字符串
   * @param questionEntities 所有题目
   */
  private void completionHandler(StringBuilder builder, List<TheoryKnowledgeQuestionEntity> questionEntities) {
    for (int i = 0; i < questionEntities.size(); i++) {
      TheoryKnowledgeQuestionEntity entity = questionEntities.get(i);
      //题目需要把$_$替换成（）
      String topic = (i + 1) + "、" + entity.getTopic() + "\r\n";
      builder.append(topic.replaceAll("\\$_\\$", "（）"));

      //答案
      String answer = "答案：" + entity.getAnswer()
          .replaceAll("\"", "")
          .replaceAll("]", "")
          .substring(1)
          + "\n\r";
      builder.append(answer);
    }
  }

  public List<TheoryKnowledgeQuestionEntity> exportQuestionByLevelId(HttpServerResponse response, String levelId) {
    return theoryKnowledgeQuestionDao.findAllByLevelId(levelId);
  }
}
