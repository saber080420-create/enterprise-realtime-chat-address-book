-- Local, explicitly authorized test fixture for account 110 (nickname b).
-- No other members, no WebSocket broadcasts, and no existing business rows changed.
SET NAMES utf8mb4;
START TRANSACTION;
SET @demo_owner = (SELECT id FROM user WHERE id=110 AND nickname='b' AND status='active');
INSERT INTO chat_group(group_name,description,creator_id,create_time,update_time)
SELECT 'AI摘要验收-20260908-01','合成测试群，仅用于摘要验收，不代表真实工作安排',@demo_owner,NOW(),NOW()
WHERE @demo_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM chat_group WHERE group_name='AI摘要验收-20260908-01' AND creator_id=@demo_owner);
SET @demo_group = (SELECT id FROM chat_group WHERE group_name='AI摘要验收-20260908-01' AND creator_id=@demo_owner);
INSERT INTO chat_group_member(group_id,user_id,role,join_time)
SELECT @demo_group,@demo_owner,'owner',DATE_SUB(NOW(),INTERVAL 10 MINUTE)
WHERE NOT EXISTS(SELECT 1 FROM chat_group_member WHERE group_id=@demo_group AND user_id=@demo_owner);
INSERT INTO chat_message(sender_id,receiver_id,group_id,chat_type,message_type,content,is_read,create_time,update_time)
SELECT @demo_owner,@demo_owner,@demo_group,'group','text',demo.content,1,DATE_SUB(NOW(),INTERVAL demo.minutes_ago MINUTE),NOW()
FROM (
 SELECT '【合成演示】以下消息仅测试摘要，不代表真实任务。小王和小李均为虚构角色。' AS content,4 AS minutes_ago
 UNION ALL SELECT '请小王在周五提交测试报告，报告只针对演示项目。',3
 UNION ALL SELECT '小李负责核对演示清单，截止时间暂未确定。',2
 UNION ALL SELECT '部署时间还没有确定，先不要发布。今天没有作出上线决定。',1
) demo WHERE NOT EXISTS(SELECT 1 FROM chat_message WHERE group_id=@demo_group);
COMMIT;
SELECT @demo_group AS demo_group_id,@demo_owner AS owner_id;
SELECT id,create_time FROM chat_message WHERE group_id=@demo_group ORDER BY create_time,id;
