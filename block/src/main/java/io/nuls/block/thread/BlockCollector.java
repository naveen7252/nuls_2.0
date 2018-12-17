/*
 *
 *  * MIT License
 *  * Copyright (c) 2017-2018 nuls.io
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *
 */

package io.nuls.block.thread;

import io.nuls.base.data.Block;
import io.nuls.base.data.NulsDigestData;
import io.nuls.block.cache.CacheHandler;
import io.nuls.block.constant.CommandConstant;
import io.nuls.block.constant.ConfigConstant;
import io.nuls.block.manager.ConfigManager;
import io.nuls.block.manager.ContextManager;
import io.nuls.block.message.CompleteMessage;
import io.nuls.block.message.HeightRangeMessage;
import io.nuls.block.model.Node;
import io.nuls.block.service.BlockService;
import io.nuls.block.utils.BlockDownloadUtils;
import io.nuls.block.utils.BlockUtil;
import io.nuls.block.utils.module.NetworkUtil;
import io.nuls.tools.core.annotation.Autowired;
import io.nuls.tools.core.annotation.Component;
import io.nuls.tools.data.DoubleUtils;
import io.nuls.tools.log.Log;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 区块下载管理器
 *
 * @author captain
 * @version 1.0
 * @date 18-11-9 下午4:25
 */
@Component
@NoArgsConstructor
public class BlockCollector implements Runnable {

    /**
     * 区块下载参数
     */
    private BlockDownloaderParams params;
    private ThreadPoolExecutor executor;
    private BlockingQueue<Future<BlockDownLoadResult>> futures;
    private int chainId;
    @Autowired
    private BlockService blockService;

    public BlockCollector(int chainId, BlockingQueue<Future<BlockDownLoadResult>> futures, ThreadPoolExecutor executor, BlockDownloaderParams params) {
        this.params = params;
        this.executor = executor;
        this.futures = futures;
        this.chainId = chainId;
    }

    @Override
    public void run() {
        BlockDownLoadResult result;
        try {
            while ((result = futures.take().get()) != null) {
                if (result != null && result.isSuccess()) {
                    Node node = result.getNode();
                    node.adjustCredit(true);
                    params.getNodes().offer(node);
                    continue;
                }
                retryDownload(result);
            }
        } catch (Exception e) {
            Log.error(e);
        }
    }


    /**
     * 下载失败重试，直到成功为止
     *
     * @param result
     * @return
     */
    private void retryDownload(BlockDownLoadResult result) {
        Node node = result.getNode();
        Log.info("retry download blocks, fail node:{}, start:{}", node, result.getStartHeight());
        PriorityBlockingQueue<Node> nodes = params.getNodes();
        try {
            result.setNode(nodes.take());
        } catch (InterruptedException e) {
            Log.error(e);
        }
        node.adjustCredit(false);
        params.getNodes().offer(node);

        if (downloadBlockFromNode(result)) {
            return;
        }
        retryDownload(result);
    }

    private boolean downloadBlockFromNode(BlockDownLoadResult result) {
        BlockDownloader.Worker worker = new BlockDownloader.Worker(result.getStartHeight(), result.getSize(), chainId, result.getNode());
        FutureTask<BlockDownLoadResult> downloadThreadFuture = new FutureTask<>(worker);
        executor.execute(downloadThreadFuture);
        try {
            return downloadThreadFuture.get().isSuccess();
        } catch (Exception e) {
            Log.error(e);
        }
        return false;
    }

}