-- Phase: 用户资料与登录信息字段
-- email/phone/gender/avatar/remark：用户资料
-- status/del_flag：账号状态与删除标志（预留，当前启停仍由 enabled 控制）
-- login_ip/login_date：登录成功后回写

ALTER TABLE v5ai_user
    ADD COLUMN email        VARCHAR(50)  NOT NULL DEFAULT '',
    ADD COLUMN phone_number VARCHAR(11)  NOT NULL DEFAULT '',
    ADD COLUMN gender       VARCHAR(1)   NOT NULL DEFAULT '0',
    ADD COLUMN avatar       BIGINT,
    ADD COLUMN status       VARCHAR(1)   NOT NULL DEFAULT '0',
    ADD COLUMN del_flag     VARCHAR(1)   NOT NULL DEFAULT '0',
    ADD COLUMN login_ip     VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN login_date   TIMESTAMPTZ,
    ADD COLUMN remark       VARCHAR(500);

COMMENT ON COLUMN v5ai_user.email         IS '用户邮箱';
COMMENT ON COLUMN v5ai_user.phone_number  IS '手机号码';
COMMENT ON COLUMN v5ai_user.gender        IS '用户性别（0男 1女 2未知）';
COMMENT ON COLUMN v5ai_user.avatar        IS '头像地址';
COMMENT ON COLUMN v5ai_user.status        IS '账号状态（0正常 1停用）';
COMMENT ON COLUMN v5ai_user.del_flag      IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN v5ai_user.login_ip      IS '最后登录IP';
COMMENT ON COLUMN v5ai_user.login_date    IS '最后登录时间';
COMMENT ON COLUMN v5ai_user.remark        IS '备注';
