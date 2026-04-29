package cn.fish.initDB.mapper;

import cn.fish.initDB.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Insert("INSERT INTO chat_session (session_id, session_name, host, port, url, username, password) " +
            "VALUES (#{sessionId}, #{sessionName}, #{host}, #{port}, #{url}, #{username}, #{password}) " +
            "ON DUPLICATE KEY UPDATE session_name = #{sessionName}, host = #{host}, port = #{port}, " +
            "url = #{url}, username = #{username}, password = #{password}")
    void insertOrUpdate(ChatSession chatSession);
}