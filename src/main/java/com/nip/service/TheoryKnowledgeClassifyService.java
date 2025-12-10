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
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 09:11
 * @Description:
 */
@ApplicationScoped
public class TheoryKnowledgeClassifyService {

  private final UserService userService;
  private final TheoryKnowledgeClassifyDao classifyDao;
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
      TheoryKnowledgeClassifyEntity tkc = classifyDao.findById(entity.getId());
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

  public TheoryKnowledgeDocumentContentVO updateFileToNip(FileUpload dto, HttpServerRequest request)  {
    String token = request.getHeader(TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);
    String id = userEntity.getId();
    return new TheoryKnowledgeDocumentContentVO();
  }
}
