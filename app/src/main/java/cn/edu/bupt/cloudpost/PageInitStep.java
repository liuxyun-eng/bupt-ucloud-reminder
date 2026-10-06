package cn.edu.bupt.cloudpost;

/** Fixed labels for diagnostics. Never contains account or callback data. */
enum PageInitStep {
    SESSION("读取本机登录"),
    ROLES("读取学校身份列表"),
    MATCH_ROLE("匹配学生身份"),
    GRANTS("读取学校页面权限"),
    PARSE_GRANTS("解析学校页面权限"),
    CHECK_GRANT("核对作业页权限"),
    USER_INFO("读取学校账号信息"),
    CHECK_USER("核对学校账号信息"),
    SAVE_ROLE("保存当前身份"),
    COOKIES("写入网页登录状态"),
    STORAGE("写入网页权限存储");
    final String label;
    PageInitStep(String label){this.label=label;}
}
