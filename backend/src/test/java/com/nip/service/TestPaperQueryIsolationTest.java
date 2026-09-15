package com.nip.service;

import com.nip.common.response.Response;
import com.nip.dao.TestPaperDao;
import com.nip.dao.TestPaperQuestionDao;
import com.nip.dao.TheoryKnowledgeQuestionLevelDao;
import com.nip.dto.TestPaperDto;
import com.nip.entity.TestPaperEntity;
import com.nip.entity.TestPaperQuestionEntity;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestPaperQueryIsolationTest {
  @Inject TestPaperService service;
  @Inject UserService users;
  @Inject TestPaperDao papers;
  @Inject TestPaperQuestionDao questions;
  @Inject TheoryKnowledgeQuestionLevelDao levels;

  private final List<String> levelIds = new ArrayList<>();
  private final List<String> paperIds = new ArrayList<>();
  private String left;
  private String child;
  private String right;
  private String empty;
  private String rootPaper;
  private String childPaper;
  private String leafPaper;
  private String rightPaper;
  private String marker;

  @BeforeEach
  void seedSeparateSubtrees() {
    marker = UUID.randomUUID().toString();
    QuarkusTransaction.requiringNew().run(() -> {
      left = level("-1");
      child = level(left);
      String leaf = level(child);
      right = level("-1");
      empty = level("-1");
      rootPaper = paper(left, "root-" + marker);
      childPaper = paper(child, "selected-child-" + marker);
      leafPaper = paper(leaf, "selected-leaf-" + marker);
      rightPaper = paper(right, "selected-right-" + marker);
    });
  }

  @AfterEach
  void removeOnlyOwnedFixtures() {
    QuarkusTransaction.requiringNew().run(() -> {
      for (String id : paperIds) {
        questions.deleteAllByTestPaperId(id);
        papers.deleteById(id);
      }
      for (String id : levelIds) {
        levels.deleteById(id);
      }
    });
  }

  @Test
  void alternatingQueriesKeepSelfDescendantsNameFilterAndEmptySubtrees() {
    assertPapers(service.findTestPaperByLevelIdAndName(left, ""), rootPaper, childPaper, leafPaper);
    assertPapers(service.findTestPaperByLevelIdAndName(right, ""), rightPaper);
    assertPapers(service.findTestPaperByLevelIdAndName(child, null), childPaper, leafPaper);
    assertPapers(service.findTestPaperByLevelIdAndName(left, "selected"), childPaper, leafPaper);
    assertPapers(service.findTestPaperByLevelIdAndName(left, "missing-" + marker));
    assertPapers(service.findTestPaperByLevelIdAndName(empty, ""));
    assertPapers(service.findTestPaperByLevelIdAndName("", marker), rootPaper, childPaper, leafPaper, rightPaper);
    assertPapers(service.findTestPaperByLevelIdAndName(left, ""), rootPaper, childPaper, leafPaper);
  }

  @Test
  void overlappingQueriesNeverIncludeTheOtherRequestsSubtree() throws Exception {
    CountDownLatch leftTraversalEntered = new CountDownLatch(1);
    CountDownLatch resumeLeft = new CountDownLatch(1);
    TheoryKnowledgeQuestionLevelDao pausingLevels = new TheoryKnowledgeQuestionLevelDao() {
      @Override
      public List<TheoryKnowledgeQuestionLevelEntity> findAllByParentId(String id) {
        if (left.equals(id)) {
          leftTraversalEntered.countDown();
          await(resumeLeft);
        }
        return levels.findAllByParentId(id);
      }
    };
    // Only pause traversal; both requests still query real persisted levels, papers and questions.
    TestPaperService shared = new TestPaperService(users, papers, questions, pausingLevels);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Response<List<TestPaperDto>>> first = executor.submit(() -> QuarkusTransaction.requiringNew()
          .call(() -> shared.findTestPaperByLevelIdAndName(left, "")));
      await(leftTraversalEntered);
      Response<List<TestPaperDto>> second;
      try {
        second = QuarkusTransaction.requiringNew()
            .call(() -> shared.findTestPaperByLevelIdAndName(right, ""));
      } finally {
        resumeLeft.countDown();
      }
      Response<List<TestPaperDto>> firstResult = first.get(10, TimeUnit.SECONDS);
      assertPapers(second, rightPaper);
      assertPapers(firstResult, rootPaper, childPaper, leafPaper);
    } finally {
      resumeLeft.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "query worker must terminate before cleanup");
    }
  }

  @Test
  void malformedPaperFailureDoesNotPolluteTheNextQuery() {
    TestPaperQuestionEntity invalid = new TestPaperQuestionEntity();
    invalid.setTestPaperId(rootPaper);
    invalid.setType(99);
    invalid.setTopic("unsupported question type");
    questions.save(invalid);

    assertThrows(IllegalArgumentException.class, () -> service.findTestPaperByLevelIdAndName(left, ""));
    assertPapers(service.findTestPaperByLevelIdAndName(right, ""), rightPaper);
    assertPapers(service.findTestPaperByLevelIdAndName(empty, ""));
    assertPapers(service.findTestPaperByLevelIdAndName(child, "selected"), childPaper, leafPaper);
  }

  private String level(String parentId) {
    TheoryKnowledgeQuestionLevelEntity level = new TheoryKnowledgeQuestionLevelEntity();
    level.setParentId(parentId);
    level.setName("paper-isolation-" + marker);
    String id = levels.save(level).getId();
    levelIds.add(id);
    return id;
  }

  private String paper(String levelId, String name) {
    TestPaperEntity paper = new TestPaperEntity();
    paper.setLevelId(levelId);
    paper.setName(name);
    paper.setTotal(100);
    paper.setPassMark(60);
    String id = papers.save(paper).getId();
    paperIds.add(id);
    return id;
  }

  private static void assertPapers(Response<List<TestPaperDto>> response, String... expectedIds) {
    assertEquals(200, response.getCode());
    assertEquals(Set.of(expectedIds), response.getData().stream().map(TestPaperDto::getId).collect(Collectors.toSet()));
    assertEquals(expectedIds.length, response.getData().size());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS), "query traversal barrier timed out");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }
}
