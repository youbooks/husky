package com.lyl.husky.core.internal.schedule;

import com.lyl.husky.core.config.LiteJobConfiguration;
import com.lyl.husky.core.executor.ShardingContexts;
import com.lyl.husky.core.internal.config.ConfigurationService;
import com.lyl.husky.core.internal.storage.JobNodeStorage;
import com.lyl.husky.core.reg.base.CoordinatorRegistryCenter;

import java.sql.Ref;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 执行作业的服务
 */
public final class ExecutionService {

    private final String jobName;
    private final JobNodeStorage jobNodeStorage;
    private final ConfigurationService configService;

    public ExecutionService(final CoordinatorRegistryCenter regCenter, final String jobName){
        this.jobName = jobName;
        jobNodeStorage = new JobNodeStorage(regCenter, jobName);
        configService = new ConfigurationService(regCenter, jobName);
    }

    /**
     * 注册作业启动信息.
     *
     * @param shardingContexts 分片上下文
     */
    public void registerJobBegin(final ShardingContexts shardingContexts){
        JobRegistry.getInstance().setJobRunning(jobName, true);
        if (!configService.load(true).isMonitorExecution()){
            return;
        }
        for (int each : shardingContexts.getShardingItemParameters().keySet()){
            jobNodeStorage.fillEphemeralJobNode(ShardingNode.getRunningNode(each), "");
        }
    }

    /**
     * 注册作业完成信息
     */
    public void registerJobCompleterd(final ShardingContexts shardingContexts){
        JobRegistry.getInstance().setJobRunning(jobName, false);
        if (!configService.load(true).isMonitorExecution()){
            return;
        }
        for (int each : shardingContexts.getShardingItemParameters().keySet()){
            jobNodeStorage.removeJobNodeIfExisted(ShardingNode.getRunningNode(each));
        }
    }

    /**
     * 清除全部分片的运行状态.
     */
    public void clearAllRunningInfo(){
        clearRunningInfo(getAllItems());
    }

    private List<Integer> getAllItems() {
        int shardingTotalCount = configService.load(true).getTypeConfig().getCoreConfig().getShardingTotalCount();
        List<Integer> result = new ArrayList<>(shardingTotalCount);
        for (int i = 0; i < shardingTotalCount; i++){
            result.add(i);
        }
        return result;
    }

    /**
     * 清除分配分片项的运行状态.
     *
     * @param items 需要清理的分片项列表
     */
    public void clearRunningInfo(final List<Integer> items){
        for (int each : items){
            jobNodeStorage.removeJobNodeIfExisted(ShardingNode.getRunningNode(each));
        }
    }

    /**
     * 判断分片项中是否还有执行中的作业.
     *
     * @param items 需要判断的分片项列表
     * @return 分片项中是否还有执行中的作业
     */
    public boolean hasRunningItems(final Collection<Integer> items){
        LiteJobConfiguration jobConfig = configService.load(true);
        if(null == jobConfig || !jobConfig.isMonitorExecution()){
            return false;
        }
        for (int each : items){
            if (jobNodeStorage.isJobNodeExisted(ShardingNode.getRunningNode(each))){
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否还有执行中的作业
     */
    public boolean hasRunningItems(){
        return hasRunningItems(getAllItems());
    }

    /**
     * 如果当前分片项仍在运行则设置任务被错误执行的标记
     */
    public boolean misfireIfHashRunningItems(final Collection<Integer> items){
        if (!hasRunningItems(items)){
            return false;
        }
        setMisfire(items);
        return true;
    }

    private void setMisfire(Collection<Integer> items) {
        for (int each : items){
            jobNodeStorage.createJobNodeIfNeeded(ShardingNode.getMisfireNode(each));
        }
    }

    /**
     * 获取标记被错过执行的任务分片项
     */
    public List<Integer> getMisfiredJobItems(final Collection<Integer> items){
        List<Integer> result = new ArrayList<>(items.size());
        for (int each : items){
            if (jobNodeStorage.isJobNodeExisted(ShardingNode.getMisfireNode(each))){
                result.add(each);
            }
        }
        return result;
    }

    /**
     * 清除任务被错过执行的标记.
     *
     * @param items 需要清除错过执行的任务分片项
     */
    public void clearMisfire(final Collection<Integer> items){
        for (int each : items){
            jobNodeStorage.removeJobNodeIfExisted(ShardingNode.getMisfireNode(each));
        }
    }

    /**
     * 获取禁用的任务分片项.
     *
     * @param items 需要获取禁用的任务分片项
     * @return 禁用的任务分片项
     */
    public List<Integer> getDisabledItems(final List<Integer> items){
        List<Integer> result = new ArrayList<>(items.size());
        for (int each : items){
            if (jobNodeStorage.isJobNodeExisted(ShardingNode.getDisabledNode(each))){
                result.add(each);
            }
        }
        return result;
    }

}