-- Phase 9 运维日志：操作日志 + 登录日志（RuoYi 风格）。

CREATE TABLE IF NOT EXISTS v5ai_oper_log (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(50)  DEFAULT '',
    business_type  INT4         DEFAULT 0,
    method         VARCHAR(100) DEFAULT '',
    request_method VARCHAR(10)  DEFAULT '',
    operator_type  INT4         DEFAULT 0,
    oper_name      VARCHAR(50)  DEFAULT '',
    user_id        BIGINT       NOT NULL,
    device_type    VARCHAR(32)  DEFAULT '',
    browser        VARCHAR(50)  DEFAULT '',
    os             VARCHAR(50)  DEFAULT '',
    oper_url       VARCHAR(255) DEFAULT '',
    oper_ip        VARCHAR(128) DEFAULT '',
    oper_location  VARCHAR(255) DEFAULT '',
    oper_param     VARCHAR(4000) DEFAULT '',
    json_result    VARCHAR(4000) DEFAULT '',
    status         INT4         DEFAULT 0,
    error_msg      VARCHAR(4000) DEFAULT '',
    oper_time      TIMESTAMPTZ,
    cost_time      INT8         DEFAULT 0
);

CREATE INDEX idx_v5ai_oper_log_bt ON v5ai_oper_log (business_type);
CREATE INDEX idx_v5ai_oper_log_uid ON v5ai_oper_log (user_id);
CREATE INDEX idx_v5ai_oper_log_s ON v5ai_oper_log (status);
CREATE INDEX idx_v5ai_oper_log_ot ON v5ai_oper_log (oper_time);

COMMENT ON TABLE v5ai_oper_log IS '操作日志记录';
COMMENT ON COLUMN v5ai_oper_log.id IS '日志主键';
COMMENT ON COLUMN v5ai_oper_log.title IS '模块标题';
COMMENT ON COLUMN v5ai_oper_log.business_type IS '业务类型（0其它 1新增 2修改 3删除）';
COMMENT ON COLUMN v5ai_oper_log.method IS '方法名称';
COMMENT ON COLUMN v5ai_oper_log.request_method IS '请求方式';
COMMENT ON COLUMN v5ai_oper_log.operator_type IS '操作类别（0其它 1后台用户 2手机端用户）';
COMMENT ON COLUMN v5ai_oper_log.oper_name IS '操作人员';
COMMENT ON COLUMN v5ai_oper_log.user_id IS '操作用户ID';
COMMENT ON COLUMN v5ai_oper_log.device_type IS '设备类型';
COMMENT ON COLUMN v5ai_oper_log.browser IS '浏览器类型';
COMMENT ON COLUMN v5ai_oper_log.os IS '操作系统';
COMMENT ON COLUMN v5ai_oper_log.oper_url IS '请求URL';
COMMENT ON COLUMN v5ai_oper_log.oper_ip IS '主机地址';
COMMENT ON COLUMN v5ai_oper_log.oper_location IS '操作地点';
COMMENT ON COLUMN v5ai_oper_log.oper_param IS '请求参数';
COMMENT ON COLUMN v5ai_oper_log.json_result IS '返回参数';
COMMENT ON COLUMN v5ai_oper_log.status IS '操作状态（0正常 1异常）';
COMMENT ON COLUMN v5ai_oper_log.error_msg IS '错误消息';
COMMENT ON COLUMN v5ai_oper_log.oper_time IS '操作时间';
COMMENT ON COLUMN v5ai_oper_log.cost_time IS '消耗时间';

CREATE TABLE IF NOT EXISTS v5ai_login_info (
    id             BIGSERIAL PRIMARY KEY,
    user_name      VARCHAR(50)  DEFAULT '',
    device_type    VARCHAR(32)  DEFAULT '',
    ipaddr         VARCHAR(128) DEFAULT '',
    login_location VARCHAR(255) DEFAULT '',
    browser        VARCHAR(50)  DEFAULT '',
    os             VARCHAR(50)  DEFAULT '',
    status         VARCHAR(30)  DEFAULT '0',
    msg            VARCHAR(255) DEFAULT '',
    login_time     TIMESTAMPTZ
);

CREATE INDEX idx_v5ai_login_info_s ON v5ai_login_info (status);
CREATE INDEX idx_v5ai_login_info_lt ON v5ai_login_info (login_time);

COMMENT ON TABLE v5ai_login_info IS '系统访问记录';
COMMENT ON COLUMN v5ai_login_info.id IS '访问ID';
COMMENT ON COLUMN v5ai_login_info.user_name IS '用户账号';
COMMENT ON COLUMN v5ai_login_info.device_type IS '设备类型';
COMMENT ON COLUMN v5ai_login_info.ipaddr IS '登录IP地址';
COMMENT ON COLUMN v5ai_login_info.login_location IS '登录地点';
COMMENT ON COLUMN v5ai_login_info.browser IS '浏览器类型';
COMMENT ON COLUMN v5ai_login_info.os IS '操作系统';
COMMENT ON COLUMN v5ai_login_info.status IS '登录状态（0正常 1异常）';
COMMENT ON COLUMN v5ai_login_info.msg IS '提示消息';
COMMENT ON COLUMN v5ai_login_info.login_time IS '访问时间';
