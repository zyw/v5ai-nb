# 数据库约定

## 表设计公共字段（PostgreSql）
 - id          主键、类型bigserial NOT NULL,
 - status      状态字段，默认值'0' 操作状态（0正常 1异常）,
 - del_flag    删除标志字段，默认值'0',character(1) DEFAULT '0' 删除标志（0代表存在 2代表删除）
 - create_by   创建人字段，
 - created_at  创建时间字段 类型timestamp without time zone,
 - updated_by  更新人字段，
 - updated_at  更新时间字段 类型timestamp without time zone,
 - remark      描述 类型varchar(1000)

## 表设计公共字段（MySql）
 - id          主键、类型bigint NOT NULL AUTO_INCREMENT,
 - status      状态字段，默认值'0' 操作状态（0正常 1异常）char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',,
 - del_flag    char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
 - create_by   创建人字段，
 - created_at  创建时间字段 类型datetime,
 - updated_by  更新人字段，
 - updated_at  更新时间字段 类型datetime,
 - remark      描述 类型varchar(1000)
