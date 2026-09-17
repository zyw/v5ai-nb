-- V34：用户/角色/菜单/客户端主键改为数据库自增（BIGSERIAL）
--
-- 背景：这四张表的 id 建表时即为 BIGSERIAL（见 V18），但实体未声明 IdType.AUTO，
--       MyBatis-Plus 按全局配置 idType=ASSIGN_ID 自行生成雪花 ID 并显式写入，
--       数据库序列因此长期未被推进。实体改为 IdType.AUTO 后，插入不再带 id 列，主键由序列生成。
--
-- 为什么需要本迁移：序列一旦落后于表内既有主键，自增插入就会撞主键。
--       已确认的落后点：V18 用位置插入写入了 plm_client 的种子行（id = 1），
--       该行未消耗序列，序列的下一个值仍是 1，改自增后首条新增客户端会违反主键约束。
--
-- 推进口径：只按「非雪花主键」（id < 1000000000000）的最大值推进序列。
--       历史上有行由 MP 雪花算法写入（量级 1.7e18 ~ 2.1e18），远大于序列可达范围，
--       不会与后续自增值冲突；若用它们推进，序列会被推到雪花量级，新行主键又会变成大数。
--       表内没有非雪花行时取 1，使首个自增值从 2 开始（序列最小值是 1，不能 setval 到 0）。
--       语句带 where 守卫：列上没有自有序列时静默跳过，避免部署启动失败。

select setval(pg_get_serial_sequence('plm_user', 'id')::regclass,
              (select coalesce(max(id), 1) from plm_user where id < 1000000000000))
where pg_get_serial_sequence('plm_user', 'id') is not null;

select setval(pg_get_serial_sequence('plm_role', 'id')::regclass,
              (select coalesce(max(id), 1) from plm_role where id < 1000000000000))
where pg_get_serial_sequence('plm_role', 'id') is not null;

select setval(pg_get_serial_sequence('plm_menu', 'id')::regclass,
              (select coalesce(max(id), 1) from plm_menu where id < 1000000000000))
where pg_get_serial_sequence('plm_menu', 'id') is not null;

select setval(pg_get_serial_sequence('plm_client', 'id')::regclass,
              (select coalesce(max(id), 1) from plm_client where id < 1000000000000))
where pg_get_serial_sequence('plm_client', 'id') is not null;