mod comprehensive;
mod exam;
mod question_level;
mod test_paper;
mod theory;
mod theory_detail;
mod theory_file;
mod theory_question;
mod theory_test;

use self::theory::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::theory::comprehensive;
    use crate::domain::theory::exam;
    use crate::domain::theory::question_level;
    use crate::domain::theory::test_paper;
    use crate::domain::theory::theory_detail;
    use crate::domain::theory::theory_file;
    use crate::domain::theory::theory_question;
    use crate::domain::theory::theory_test;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let comprehensive_routes = Router::new()
        .route(
            "/getUserOverallInfo",
            get(comprehensive::get_user_overall_info),
        )
        .route("/getTheoryYear", get(comprehensive::get_theory_year))
        .route(
            "/getTheoryTestYear",
            get(comprehensive::get_theory_test_year),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let theory_routes = Router::new()
        .route("/listPageClassify", post(list_classify))
        .route("/getBasicTheory", post(list_basic))
        .route("/getDutyBusiness", post(duty_business))
        .route("/getChargeEquipment", post(charge_equipment))
        .route("/getBasicTheoryOpen", post(list_open))
        .route("/saveTheoryKnowledgeRecord", post(save_record))
        .route("/recordStatistice", post(record_statistics))
        .route("/gradeCount", post(grade_count))
        .route("/getById", post(theory_detail::by_id))
        .route("/getByIdAndToken", post(theory_detail::by_id_and_token))
        .route("/uploadFileToNip", post(theory_file::upload))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_theory = Router::new()
        .route("/saveTheoryKnowledge", post(save_knowledge))
        .route("/addClassify", post(save_classify))
        .route("/removeClassify", post(delete_classify))
        .route("/deleteThroyKnowledgeById", post(delete_knowledge))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let test = Router::new()
        .route(
            "/saveUserKnowledgeSwfTestContent",
            post(theory_test::save_user),
        )
        .route("/getTestBySwfId", post(theory_test::by_swf))
        .route("/getByKnowledgeSwfIdAndEnable", post(theory_test::enabled))
        .route(
            "/getTestContentByUserIdAndKnowledgeId",
            post(theory_test::by_knowledge),
        )
        .route(
            "/getTestContentByUserIdAndKnowledgeSwfId",
            post(theory_test::by_swf_user),
        )
        .route("/getTestContentById", post(theory_test::by_id))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_test = Router::new()
        .route("/saveTheoryKnowledgeTest", post(theory_test::save))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let question = Router::new()
        .route(
            "/findAllTheoryKnowledgeQuestionLevel",
            post(theory_question::list_levels),
        )
        .route(
            "/findAllQuestionByLevelId",
            post(theory_question::list_questions),
        )
        .route("/exportTemplate", post(theory_question::export_template))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_question = Router::new()
        .route(
            "/saveTheoryKnowledgeQuestion",
            post(theory_question::save_question),
        )
        .route(
            "/saveTheoryKnowledgeQuestionLevel",
            post(question_level::save_level),
        )
        .route(
            "/exportQuestionByLevelId",
            post(theory_question::export_questions),
        )
        .route(
            "/deleteTheoryKnowledgeQuestionLevelById",
            post(theory_question::delete_level),
        )
        .route("/saveBatch", post(theory_question::save_batch))
        .route(
            "/deleteTheoryKnowledgeQuestion",
            post(theory_question::delete_question),
        )
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let paper = Router::new()
        .route("/findAllTestPaper", post(test_paper::list))
        .route("/findTestPaperById", post(test_paper::by_id))
        .route(
            "/findTestPaperByLevelIdAndName",
            post(test_paper::by_level_name),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_paper = Router::new()
        .route("/saveTestPaper", post(test_paper::save))
        .route("/deleteTestPaper", post(test_paper::delete))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let exam = Router::new()
        .route("/findAllTheoryKnowledgeExam", post(exam::list))
        .route("/findTheoryKnowledgeExamById", post(exam::detail))
        .route("/saveTheoryKnowledgeExamSelfTesting", post(exam::self_test))
        .route("/listPageSelfTesting", post(exam::self_list))
        .route("/finishSelfTesting", post(exam::finish_self_test))
        .route(
            "/teacherStartTheoryKnowledgeExam",
            post(exam::teacher_start),
        )
        .route("/studentChangeExamState", post(exam::student_state))
        .route("/studentSaveExamRealtimeContont", post(exam::realtime))
        .route("/examineAnalyse", post(exam::analyse))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_exam = Router::new()
        .route("/savetheoryKnowledgeExam", post(exam::save_formal))
        .route("/deleteTheoryKnowledgeExam", post(exam::delete_exam))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let exam_user = Router::new()
        .route("/findAllTheoryKnowledgeExamUser", post(exam::user_list))
        .route("/findExamUser", post(exam::find_exam_user))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_exam_user = Router::new()
        .route("/teacherUploadScore", post(exam::teacher_upload_score))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/comprehensive", comprehensive_routes)
        .nest("/api/theoryKnowledge", theory_routes.merge(admin_theory))
        .nest("/api/theoryKnowledgeTest", test.merge(admin_test))
        .nest(
            "/api/theoryKnowledgeQuestion",
            question.merge(admin_question),
        )
        .nest("/api/theoryKnowledgeTestPaper", paper.merge(admin_paper))
        .nest("/api/theoryKnowledgeExam", exam.merge(admin_exam))
        .nest(
            "/api/theoryKnowledgeExamUser",
            exam_user.merge(admin_exam_user),
        );
    router
}

#[cfg(test)]
mod tests;
