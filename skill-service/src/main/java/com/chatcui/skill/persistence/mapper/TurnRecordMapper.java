package com.chatcui.skill.persistence.mapper;

import com.chatcui.skill.persistence.model.TurnRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TurnRecordMapper {

    boolean existsBySessionTurnSeq(
            @Param("sessionId") String sessionId,
            @Param("turnId") String turnId,
            @Param("seq") Long seq
    );

    Optional<TurnRecord> findLatestBySessionTurn(
            @Param("sessionId") String sessionId,
            @Param("turnId") String turnId
    );

    int insert(TurnRecord record);

    List<TurnRecord> listHistoryBySession(
            @Param("tenantId") String tenantId,
            @Param("clientId") String clientId,
            @Param("sessionId") String sessionId,
            @Param("cursorTurnId") String cursorTurnId,
            @Param("limit") int limit
    );

    boolean existsSession(
            @Param("tenantId") String tenantId,
            @Param("clientId") String clientId,
            @Param("sessionId") String sessionId
    );

    boolean existsTurnInSession(
            @Param("tenantId") String tenantId,
            @Param("clientId") String clientId,
            @Param("sessionId") String sessionId,
            @Param("turnId") String turnId
    );
}
