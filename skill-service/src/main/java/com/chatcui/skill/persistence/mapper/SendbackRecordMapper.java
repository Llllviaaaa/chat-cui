package com.chatcui.skill.persistence.mapper;

import com.chatcui.skill.persistence.model.SendbackRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface SendbackRecordMapper {

    int insert(SendbackRecord record);

    Optional<SendbackRecord> findByRequestId(@Param("requestId") String requestId);
}

