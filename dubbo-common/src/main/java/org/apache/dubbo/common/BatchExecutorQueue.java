/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批量执行队列
 * 用于将多个任务项批量处理的队列，提高执行效率
 * @param <T> 队列中元素的类型
 */
public class BatchExecutorQueue<T> {

    /** 默认队列大小 */
    static final int DEFAULT_QUEUE_SIZE = 128;
    /** 任务队列 */
    private final Queue<T> queue;
    /** 调度状态标记 */
    private final AtomicBoolean scheduled;
    /** 批处理块大小 */
    private final int chunkSize;

    /**
     * 默认构造函数，使用默认队列大小
     */
    public BatchExecutorQueue() {
        this(DEFAULT_QUEUE_SIZE);
    }

    /**
     * 构造函数
     * 
     * @param chunkSize 批处理块大小
     */
    public BatchExecutorQueue(int chunkSize) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.scheduled = new AtomicBoolean(false);
        this.chunkSize = chunkSize;
    }

    /**
     * 将任务项加入队列并调度执行
     * 
     * @param message 任务项
     * @param executor 执行器
     */
    public void enqueue(T message, Executor executor) {
        queue.add(message);
        scheduleFlush(executor);
    }

    /**
     * 调度刷新执行
     * 
     * @param executor 执行器
     */
    protected void scheduleFlush(Executor executor) {
        if (scheduled.compareAndSet(false, true)) {
            executor.execute(() -> this.run(executor));
        }
    }

    /**
     * 执行队列中的任务
     * 
     * @param executor 执行器
     */
    private void run(Executor executor) {
        try {
            Queue<T> snapshot = new LinkedList<>();
            T item;
            while ((item = queue.poll()) != null) {
                snapshot.add(item);
            }
            int i = 0;
            boolean flushedOnce = false;
            while ((item = snapshot.poll()) != null) {
                if (snapshot.size() == 0) {
                    flushedOnce = false;
                    break;
                }
                if (i == chunkSize) {
                    i = 0;
                    flush(item);
                    flushedOnce = true;
                } else {
                    prepare(item);
                    i++;
                }
            }
            if (!flushedOnce && item != null) {
                flush(item);
            }
        } finally {
            scheduled.set(false);
            if (!queue.isEmpty()) {
                scheduleFlush(executor);
            }
        }
    }

    /**
     * 准备任务项
     * 子类可以重写此方法来实现特定的准备逻辑
     * 
     * @param item 任务项
     */
    protected void prepare(T item) {}

    /**
     * 刷新执行任务项
     * 子类必须重写此方法来实现具体的任务执行逻辑
     * 
     * @param item 任务项
     */
    protected void flush(T item) {}
}
