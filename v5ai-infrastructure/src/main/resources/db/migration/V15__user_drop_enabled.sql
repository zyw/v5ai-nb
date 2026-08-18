-- Phase: 用户启停改用 status 字段，移除 enabled 列
-- V14 已新增 status（'0'正常 '1'停用），此处删除冗余的 enabled 列

ALTER TABLE v5ai_user DROP COLUMN enabled;
