// 仅可复用的业务资料。训练实例、成绩、答卷、学习记录和账号会话不进入安装种子。
const businessDataGroups = {
    theoryStudy: ['t_theory_knowledge_classify', 't_theory_knowledge', 't_theory_knowledge_swf',
        't_theory_knowledge_test', 't_theory_knowledge_test_content'],
    theoryQuestionBank: ['t_theory_question_level', 't_theory_question'],
    preJob: ['t_key_points', 't_receive_key_points', 't_entering_key_points',
        't_telegram_train_setting', 't_ticker_tape_train_setting', 't_ticker_tape_train_stage_setting',
        't_post_ticker_tape_train_setting', 't_entering_exercise_word_stock',
        't_military_term_data', 't_post_radiotelephone_term_data'],
    scoringRules: ['t_grading_rule', 't_general_group_net_rule', 't_post_train_global_rule', 'device_scoring_rule',
        't_device_type', 't_device', 't_device_description', 't_equipment_device'],
    fixedMessages: ['t_cable_type', 't_cable', 't_cable_floor'],
    articles: ['t_post_entering_exercise_word_stock']
};
const systemTables = ['t_menus', 't_menus_button', 't_role', 't_role_menus'];
module.exports = {businessDataGroups, systemTables};
