package cn.fish.initDB.repository;

import cn.fish.initDB.entity.ExportJob;
import com.baomidou.mybatisplus.extension.repository.IRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExportJobRepository extends IRepository<ExportJob> {

    /**
     * 领取一条 PENDING 任务并原子置为 RUNNING；多实例争抢失败返回 null。
     */
    ExportJob pollOnePending();

    /**
     * 已过期且尚未标记为 EXPIRED 的任务（用于清理调度）。
     */
    List<ExportJob> listExpiredForCleanup(LocalDateTime now);
}
