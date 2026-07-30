package com.langxi.babydiary.platform.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RetentionMapper {
    @Insert(
            """
            INSERT INTO sync_retention(space_id,baseline_cursor,updated_at)
            SELECT space_id,MAX(change_seq),UTC_TIMESTAMP(6) FROM sync_change
            WHERE created_at<#{cutoff} GROUP BY space_id
            ON DUPLICATE KEY UPDATE baseline_cursor=GREATEST(baseline_cursor,VALUES(baseline_cursor)),
              updated_at=UTC_TIMESTAMP(6)
            """)
    int recordSyncBaselines(LocalDateTime cutoff);

    @Delete("DELETE FROM sync_change WHERE created_at<#{cutoff} ORDER BY change_seq LIMIT #{limit}")
    int deleteSyncChanges(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete("DELETE FROM sync_operation WHERE expires_at<#{now} ORDER BY expires_at LIMIT #{limit}")
    int deleteExpiredSyncOperations(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete(
            """
            DELETE FROM auth_session
            WHERE expires_at<#{cutoff} OR (revoked_at IS NOT NULL AND revoked_at<#{cutoff})
            ORDER BY session_id LIMIT #{limit}
            """)
    int deleteExpiredAuthSessions(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete(
            """
            DELETE FROM account_token
            WHERE expires_at<#{cutoff} OR (used_at IS NOT NULL AND used_at<#{cutoff})
            ORDER BY token_id LIMIT #{limit}
            """)
    int deleteExpiredAccountTokens(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete(
            "DELETE FROM recovery_code WHERE used_at IS NOT NULL AND used_at<#{cutoff} ORDER BY recovery_code_id LIMIT #{limit}")
    int deleteUsedRecoveryCodes(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete(
            "DELETE FROM background_job WHERE completed_at IS NOT NULL AND completed_at<#{cutoff} ORDER BY job_id LIMIT #{limit}")
    int deleteCompletedJobs(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete(
            """
            DELETE FROM outbox_event
            WHERE (processed_at IS NOT NULL AND processed_at<#{cutoff})
               OR (failed_at IS NOT NULL AND failed_at<#{cutoff})
            ORDER BY event_id LIMIT #{limit}
            """)
    int deleteCompletedOutboxEvents(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
