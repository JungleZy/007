package com.nip.service;

import cn.hutool.core.util.ObjectUtil;
import com.nip.common.constants.TheoryKnowledgeClassifyTypeEnum;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TheoryKnowledgeClassifyDao;
import com.nip.dto.TheoryKnowledgeClassifyDto;
import com.nip.dto.vo.TheoryKnowledgeClassifyPageVO;
import com.nip.dto.vo.TheoryKnowledgeClassifyVO;
import com.nip.dto.vo.TheoryKnowledgeDocumentContentVO;
import com.nip.entity.TheoryKnowledgeClassifyEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 09:11
 * @Description:
 */
@ApplicationScoped
public class TheoryKnowledgeClassifyService {

  private final UserService userService;
  private final TheoryKnowledgeClassifyDao classifyDao;

  /** 允许直接读取的纯文本后缀；其余格式（含 Office）一律拒绝。 */
  private static final Set<String> TEXT_SUFFIXES = Set.of("txt", "md", "csv");

  /** {@link TheoryKnowledgeDocumentContentVO#getType()} 的「word 文档内容」取值。 */
  private static final int WORD_CONTENT_TYPE = 2;
  @Inject
  public TheoryKnowledgeClassifyService(UserService userService, TheoryKnowledgeClassifyDao classifyDao) {
    this.userService = userService;
    this.classifyDao = classifyDao;
  }

  @Transactional
  public TheoryKnowledgeClassifyVO add(TheoryKnowledgeClassifyDto dto, String token)  {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeClassifyEntity entity = PojoUtils.convertOne(dto, TheoryKnowledgeClassifyEntity.class, (d, e) -> {
      e.setCreateId(userEntity.getId());
      if (e.getCreateTime() == null) {
        e.setCreateTime(LocalDateTime.now());
      }
    });
    if (ObjectUtil.isEmpty(entity.getId())) {
      entity.setId(null);
      classifyDao.saveAndFlush(entity);
    } else {
      TheoryKnowledgeClassifyEntity tkc = classifyDao.findByIdOptional(entity.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到该分类"));
      tkc.setType(dto.getType());
      tkc.setName(dto.getName());
    }
    return PojoUtils.convertOne(entity, TheoryKnowledgeClassifyVO.class);
  }

  @Transactional(rollbackOn = Exception.class)
  public void remove(TheoryKnowledgeClassifyDto dto) {
    classifyDao.deleteById(dto.getId());
  }

  public TheoryKnowledgeClassifyPageVO listPageClassify() {
    TheoryKnowledgeClassifyPageVO pageVO = new TheoryKnowledgeClassifyPageVO();
    //查收出所有
    List<TheoryKnowledgeClassifyEntity> all = classifyDao.findAll(Sort.by("createTime").descending()).list();

    //按type分类
    List<TheoryKnowledgeClassifyVO> convert = PojoUtils.convert(all, TheoryKnowledgeClassifyVO.class);
    //分别取出
    Map<Integer, List<TheoryKnowledgeClassifyVO>> classifyMap = convert.stream().collect(
      Collectors.groupingBy(TheoryKnowledgeClassifyVO::getType));

    pageVO.setDifficultyList(Optional.ofNullable(classifyMap.get(TheoryKnowledgeClassifyTypeEnum.difficulty.getType()))
                                     .orElseGet(ArrayList::new));
    pageVO.setSpecialtyList(Optional.ofNullable(classifyMap.get(TheoryKnowledgeClassifyTypeEnum.specialty.getType()))
                                    .orElseGet(ArrayList::new));
    return pageVO;
  }

  /**
   * 读取上传的纯文本文档并返回其内容。
   *
   * <p><b>能力边界</b>：只支持纯文本（{@code .txt}/{@code .md}/{@code .csv}）。
   * {@code .docx}/{@code .pptx} 的解析与「PPT 转图片」（{@code imgUrls}、{@code type=1}）
   * 需要 poi，本仓刻意不引该依赖（{@code pom.xml} 里 {@code quarkus-poi} 与
   * {@code quarkus-awt} 均被注掉，且三平台 native 产物构建对其有风险），
   * 因此 Office 格式一律拒绝而**不是**静默返回空内容——后者正是本轮整改要消灭的假成功。
   * 需要 Word/PPT 时由前端解析后走既有的课件内容保存路径。
   */
  public TheoryKnowledgeDocumentContentVO readDocumentContent(FileUpload file, String token) {
    userService.getUserByToken(token);
    if (file == null || file.uploadedFile() == null) {
      throw new IllegalArgumentException("未收到上传文件");
    }
    if (file.size() > 10L * 1024 * 1024) {
      throw new IllegalArgumentException("上传文件不能超过10MiB");
    }
    String fileName = file.fileName() == null ? "" : file.fileName();
    String suffix = fileName.contains(".")
        ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
        : "";
    if (!TEXT_SUFFIXES.contains(suffix)) {
      throw new IllegalArgumentException(
          "仅支持纯文本文档（" + String.join("/", TEXT_SUFFIXES) + "）；Word/PPT 请由前端解析后提交内容");
    }
    String content;
    try {
      content = Files.readString(file.uploadedFile(), StandardCharsets.UTF_8);
    } catch (IOException | java.io.UncheckedIOException e) {
      throw new IllegalArgumentException("文档不是 UTF-8 纯文本，无法读取：" + fileName, e);
    }
    if (content.isBlank()) {
      throw new IllegalArgumentException("文档内容为空：" + fileName);
    }
    TheoryKnowledgeDocumentContentVO vo = new TheoryKnowledgeDocumentContentVO();
    vo.setType(WORD_CONTENT_TYPE);
    vo.setWordContent(content);
    vo.setImgUrls(List.of());
    return vo;
  }
}
