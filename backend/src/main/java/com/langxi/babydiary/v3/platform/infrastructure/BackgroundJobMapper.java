package com.langxi.babydiary.v3.platform.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BackgroundJobMapper {
    @Update("""
            <script>
            UPDATE background_job
            SET status='RUNNING',claimed_at=#{now},claimed_by=#{claimToken},
                attempt_count=attempt_count+1,last_error=NULL
            WHERE status='PENDING' AND available_at&lt;=#{now} AND job_type IN
              <foreach collection='types' item='type' open='(' separator=',' close=')'>#{type}</foreach>
            ORDER BY available_at,job_id LIMIT 1
            </script>
            """)
    int claim(@Param("claimToken") String claimToken, @Param("now") LocalDateTime now,
              @Param("types") List<String> types);

    @Insert("""
            INSERT IGNORE INTO background_job(public_id,space_id,created_by,job_type,dedupe_key,status,payload,
              max_attempts,available_at,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{createdBy},#{jobType},#{dedupeKey},'PENDING',#{payload},
              #{maxAttempts},#{availableAt},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    int enqueue(NewJob job);

    @Select("""
            SELECT job_id,job_type,payload,attempt_count,max_attempts
            FROM background_job WHERE status='RUNNING' AND claimed_by=#{claimToken}
            """)
    JobRow findClaimed(String claimToken);

    @Update("""
            UPDATE background_job SET status='SUCCEEDED',result=#{result},completed_at=#{now},
              claimed_at=NULL,claimed_by=NULL,last_error=NULL
            WHERE job_id=#{jobId} AND status='RUNNING' AND claimed_by=#{claimToken}
            """)
    int succeed(@Param("jobId") long jobId, @Param("claimToken") String claimToken,
                @Param("result") String result, @Param("now") LocalDateTime now);

    @Update("""
            <script>
            UPDATE background_job SET
              status=<choose><when test="terminal">'FAILED'</when><otherwise>'PENDING'</otherwise></choose>,
              available_at=#{availableAt},claimed_at=NULL,claimed_by=NULL,last_error=#{error},
              completed_at=<choose><when test="terminal">#{now}</when><otherwise>NULL</otherwise></choose>
            WHERE job_id=#{jobId} AND status='RUNNING' AND claimed_by=#{claimToken}
            </script>
            """)
    int fail(@Param("jobId") long jobId, @Param("claimToken") String claimToken,
             @Param("terminal") boolean terminal, @Param("availableAt") LocalDateTime availableAt,
             @Param("error") String error, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE background_job SET status='PENDING',available_at=#{now},claimed_at=NULL,claimed_by=NULL,
              last_error='任务执行超时，已自动重新排队'
            WHERE status='RUNNING' AND claimed_at<#{staleBefore} AND attempt_count<max_attempts
            """)
    int recoverRetryable(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE background_job SET status='FAILED',completed_at=#{now},claimed_at=NULL,claimed_by=NULL,
              last_error='任务执行超时且已达到最大重试次数'
            WHERE status='RUNNING' AND claimed_at<#{staleBefore} AND attempt_count>=max_attempts
            """)
    int failExhausted(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    record JobRow(long jobId, String jobType, String payload, int attemptCount, int maxAttempts) {}

    record NewJob(byte[] publicId, Long spaceId, Long createdBy, String jobType, String dedupeKey,
                  String payload, int maxAttempts, LocalDateTime availableAt) {}
}
