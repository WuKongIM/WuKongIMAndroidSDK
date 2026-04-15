-- 补充高频查询路径的缺失索引，解决消息加载和同步场景下的ANR
-- message_id: queryWithMsgIds() 全表扫描
CREATE INDEX IF NOT EXISTS idx_msg_message_id ON message(message_id);
-- (channel_id, channel_type, order_seq): queryMessages() 分页排序，打开聊天的核心查询
CREATE INDEX IF NOT EXISTS idx_msg_channel_order ON message(channel_id, channel_type, order_seq);
-- (channel_id, channel_type, message_seq): getMaxMessageSeq/getDeletedCount 全扫描
CREATE INDEX IF NOT EXISTS idx_msg_channel_seq ON message(channel_id, channel_type, message_seq);
-- reminders(channel_id, channel_type, done): queryWithChannelAndDone() 每次打开会话触发
CREATE INDEX IF NOT EXISTS idx_reminders_channel_done ON reminders(channel_id, channel_type, done);
-- flame + is_deleted: queryWithFlame() 阅后即焚查询
CREATE INDEX IF NOT EXISTS idx_msg_flame ON message(flame, is_deleted);
