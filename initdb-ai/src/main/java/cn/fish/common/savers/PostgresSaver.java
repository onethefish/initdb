package cn.fish.common.savers;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import static com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver.THREAD_ID_DEFAULT;
import static java.lang.String.format;

/**
 * 与 {@link com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver} 等价的 checkpoint 语义，
 * 持久化到当前应用使用的 PostgreSQL（仅依赖 {@link JdbcTemplate}，不再单独解析 JDBC URL）。
 */
public final class PostgresSaver implements BaseCheckpointSaver {

    private static final String TABLE = "initdb_graph_checkpoint_entry";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final StateSerializer stateSerializer;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean tableReady;

    public PostgresSaver(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.stateSerializer = StateGraph.DEFAULT_JACKSON_SERIALIZER;
    }

    private void ensureTable() {
        if (tableReady) {
            return;
        }
        lock.lock();
        try {
            if (tableReady) {
                return;
            }
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                            + "thread_id VARCHAR(190) NOT NULL,"
                            + "sort_ord INT NOT NULL,"
                            + "checkpoint_id VARCHAR(190) NOT NULL,"
                            + "node_id VARCHAR(512),"
                            + "next_node_id VARCHAR(512),"
                            + "state_bytes BYTEA NOT NULL,"
                            + "PRIMARY KEY (thread_id, sort_ord)"
                            + ")");
            tableReady = true;
        } finally {
            lock.unlock();
        }
    }

    private static String resolveThreadId(RunnableConfig config) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }

    private LinkedList<Checkpoint> loadAll(String threadId) {
        ensureTable();
        List<Checkpoint> rows = jdbcTemplate.query(
                "SELECT checkpoint_id, node_id, next_node_id, state_bytes FROM " + TABLE
                        + " WHERE thread_id = ? ORDER BY sort_ord ASC",
                (rs, rowNum) -> {
                    byte[] stateBytes = rs.getBytes("state_bytes");
                    Map<String, Object> state;
                    try {
                        state = stateSerializer.dataFromBytes(stateBytes);
                    } catch (IOException | ClassNotFoundException e) {
                        throw new IllegalStateException("checkpoint state 反序列化失败", e);
                    }
                    return Checkpoint.builder()
                            .id(rs.getString("checkpoint_id"))
                            .nodeId(rs.getString("node_id"))
                            .nextNodeId(rs.getString("next_node_id"))
                            .state(state != null ? state : new HashMap<>())
                            .build();
                },
                threadId);
        return new LinkedList<>(rows);
    }

    private void replaceAll(String threadId, LinkedList<Checkpoint> checkpoints) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE thread_id = ?", threadId);
            int ord = 0;
            for (Checkpoint c : checkpoints) {
                Map<String, Object> state = c.getState() != null ? c.getState() : Map.of();
                byte[] stateBytes;
                try {
                    stateBytes = stateSerializer.dataToBytes(state);
                } catch (IOException e) {
                    throw new IllegalStateException("checkpoint state 序列化失败", e);
                }
                jdbcTemplate.update(
                        "INSERT INTO " + TABLE
                                + " (thread_id, sort_ord, checkpoint_id, node_id, next_node_id, state_bytes) VALUES (?,?,?,?,?,?)",
                        threadId,
                        ord++,
                        c.getId(),
                        c.getNodeId(),
                        c.getNextNodeId(),
                        stateBytes);
            }
        });
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        lock.lock();
        try {
            LinkedList<Checkpoint> checkpoints = loadAll(resolveThreadId(config));
            return Collections.unmodifiableCollection(new ArrayList<>(checkpoints));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        lock.lock();
        try {
            LinkedList<Checkpoint> checkpoints = loadAll(resolveThreadId(config));
            if (config.checkPointId().isPresent()) {
                String id = config.checkPointId().get();
                return checkpoints.stream().filter(cp -> id.equals(cp.getId())).findFirst();
            }
            return getLast(checkpoints, config);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        lock.lock();
        try {
            String threadId = resolveThreadId(config);
            LinkedList<Checkpoint> checkpoints = loadAll(threadId);
            if (config.checkPointId().isPresent()) {
                String checkPointId = config.checkPointId().get();
                int index = IntStream.range(0, checkpoints.size())
                        .filter(i -> checkpoints.get(i).getId().equals(checkPointId))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(format("Checkpoint with id %s not found!", checkPointId)));
                checkpoints.set(index, checkpoint);
                replaceAll(threadId, checkpoints);
                return config;
            }
            checkpoints.push(checkpoint);
            replaceAll(threadId, checkpoints);
            return RunnableConfig.builder(config).checkPointId(checkpoint.getId()).build();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        lock.lock();
        try {
            String threadId = resolveThreadId(config);
            Collection<Checkpoint> snapshot = new ArrayList<>(loadAll(threadId));
            jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE thread_id = ?", threadId);
            return new Tag(threadId, snapshot);
        } finally {
            lock.unlock();
        }
    }
}
