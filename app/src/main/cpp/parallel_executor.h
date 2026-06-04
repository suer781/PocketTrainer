#pragma once
#include <thread>
#include <vector>
#include <functional>

namespace pockettrainer {

class ParallelExecutor {
public:
    ParallelExecutor(int num_threads = 0) {
        if (num_threads <= 0) {
            num_threads = std::thread::hardware_concurrency();
            if (num_threads <= 0) num_threads = 4;
        }
        num_threads_ = num_threads;
    }

    int num_threads() const { return num_threads_; }

    void parallel_for(int start, int end, std::function<void(int)> func) {
        if (end <= start) return;
        int total = end - start;
        if (total == 1 || num_threads_ == 1) {
            for (int i = start; i < end; i++) func(i);
            return;
        }
        int chunk = (total + num_threads_ - 1) / num_threads_;
        std::vector<std::thread> threads;
        for (int t = 0; t < num_threads_ && t * chunk < total; t++) {
            int s = start + t * chunk, e = std::min(s + chunk, end);
            threads.emplace_back([s, e, &func]() { for (int i = s; i < e; i++) func(i); });
        }
        for (auto& t : threads) if (t.joinable()) t.join();
    }

    static void parallel_matmul(const float* A, const float* B, float* C,
                                int M, int N, int K, const ParallelExecutor& exec) {
        exec.parallel_for(0, M, [&](int i) {
            for (int j = 0; j < N; j++) {
                float sum = 0;
                for (int k = 0; k < K; k++) sum += A[i*K+k] * B[k*N+j];
                C[i*N+j] = sum;
            }
        });
    }

private:
    int num_threads_;
};

inline ParallelExecutor& global_executor() {
    static ParallelExecutor instance;
    return instance;
}

}  // namespace pockettrainer