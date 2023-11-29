package com.eficode.atlassian.jiraInstanceManager.concurrent

import java.util.concurrent.*

public class ConcurrentExecutorHelper {

    public static <T> List<T> executeInParallel(Closure<List<T>> closure) {
        ExecutorService pool = Executors.newFixedThreadPool(10)
        ExecutorCompletionService<T> ecs =  new ExecutorCompletionService<T>(pool)

        List<Future<T>> futures = []
        List<T> results = []
        try {
            closure.call({ task ->
                futures << ecs.submit(task)
            })
            futures.each { future ->
                results << future.get()
            }
        } finally {
            pool.shutdown()
        }
        return results
    }


}
