package cn.fish.initDB.repository.impl;

import cn.fish.initDB.enums.ExportJobStatus;
import cn.fish.initDB.entity.ExportJob;
import cn.fish.initDB.mapper.ExportJobMapper;
import cn.fish.initDB.repository.ExportJobRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ExportJobRepositoryImpl extends CrudRepository<ExportJobMapper, ExportJob> implements ExportJobRepository {

    @Override
    public ExportJob pollOnePending() {
        ExportJob job = getBaseMapper().selectOne(
                new LambdaQueryWrapper<ExportJob>()
                        .eq(ExportJob::getStatus, ExportJobStatus.PENDING.name())
                        .orderByAsc(ExportJob::getCreatedTime)
                        .last("LIMIT 1"));
        if (job == null) {
            return null;
        }
        LambdaUpdateWrapper<ExportJob> uw = new LambdaUpdateWrapper<>();
        uw.eq(ExportJob::getId, job.getId())
          .eq(ExportJob::getStatus, ExportJobStatus.PENDING.name())
          .set(ExportJob::getStatus, ExportJobStatus.RUNNING.name());
        int n = getBaseMapper().update(null, uw);
        if (n != 1) {
            return null;
        }
        job.setStatus(ExportJobStatus.RUNNING.name());
        return job;
    }

    @Override
    public List<ExportJob> listExpiredForCleanup(LocalDateTime now) {
        return list(new LambdaQueryWrapper<ExportJob>()
                .lt(ExportJob::getExpiresAt, now)
                .ne(ExportJob::getStatus, ExportJobStatus.EXPIRED.name()));
    }
}
