#pragma once
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <random>
#include <algorithm>
#include <stdexcept>
#include <cstdint>

namespace pocket_trainer {

/**
 * 通用文本数据集 — 支持 .txt / .jsonl / .json / .csv
 * 用户提供什么格式就读什么格式，训练出问题概不负责
 *
 * 内部逻辑：
 * - .jsonl → 逐行读，拼接 instruction+input+output 或任意 text 字段
 * - .json  → 读数组，同上
 * - .csv   → 跳表头，拼接所有列
 * - .txt   → 按空行分段，每段是一个样本
 * - 其他   → 当纯文本读
 */
class TextDataset {
public:
    TextDataset(const std::string& path, int seq_len, int seed = 42)
        : seq_len_(seq_len)
    {
        load(path);
        if (token_ids_.empty()) {
            throw std::runtime_error("Empty dataset: " + path);
        }
        shuffle(seed);
    }

    int size() const { return (int)token_ids_.size() / seq_len_; }

    std::vector<int32_t> get_batch(int idx, int batch_size) const {
        std::vector<int32_t> batch;
        batch.reserve(batch_size * seq_len_);
        for (int b = 0; b < batch_size; b++) {
            int offset = (idx * batch_size + b) * seq_len_;
            if (offset + seq_len_ > (int)token_ids_.size()) {
                offset = 0;  // wrap around
            }
            batch.insert(batch.end(),
                token_ids_.begin() + offset,
                token_ids_.begin() + offset + seq_len_);
        }
        return batch;
    }

    void shuffle(int seed) {
        // 重新组织 token 序列
        std::mt19937 rng(seed);
        // 把 token_ids_ 切成 seq_len 长的块，然后打乱块顺序
        int num_chunks = (int)token_ids_.size() / seq_len_;
        std::vector<int> indices(num_chunks);
        std::iota(indices.begin(), indices.end(), 0);
        std::shuffle(indices.begin(), indices.end(), rng);

        std::vector<int32_t> shuffled;
        shuffled.reserve(num_chunks * seq_len_);
        for (int i : indices) {
            shuffled.insert(shuffled.end(),
                token_ids_.begin() + i * seq_len_,
                token_ids_.begin() + (i + 1) * seq_len_);
        }
        token_ids_ = std::move(shuffled);
    }

private:
    int seq_len_;
    std::vector<int32_t> token_ids_;

    void load(const std::string& path) {
        // 检测格式
        std::string ext = path.substr(path.rfind('.'));
        std::vector<std::string> texts;

        if (ext == ".jsonl" || ext == ".json") {
            texts = load_json_like(path, ext == ".jsonl");
        } else if (ext == ".csv") {
            texts = load_csv(path);
        } else {
            // .txt 和其他：按空行分段
            texts = load_plain_text(path);
        }

        // 简单字符级 tokenizer（兼容性最好，不需要词表文件）
        for (const auto& text : texts) {
            for (unsigned char c : text) {
                token_ids_.push_back(static_cast<int32_t>(c));
            }
            // 样本间加分隔
            token_ids_.push_back(0);  // null byte as separator
        }
    }

    static std::string extract_text_from_json_line(const std::string& line) {
        // 简易 JSON 解析：找 text / instruction / output 字段
        std::vector<std::string> parts;
        for (const auto& key : {"\"text\"", "\"instruction\"", "\"input\"", "\"output\"", "\"content\""}) {
            auto pos = line.find(key);
            if (pos != std::string::npos) {
                // 找到 key 后面的值
                auto colon = line.find(':', pos + strlen(key));
                if (colon == std::string::npos) continue;
                auto val_start = line.find_first_not_of(" \t", colon + 1);
                if (val_start == std::string::npos) continue;

                if (line[val_start] == '"') {
                    // 字符串值 — 找匹配的引号（跳过转义）
                    val_start++;
                    std::string val;
                    for (size_t i = val_start; i < line.size(); i++) {
                        if (line[i] == '\\' && i + 1 < line.size()) {
                            char next = line[i + 1];
                            switch (next) {
                                case 'n': val += '\n'; break;
                                case 't': val += '\t'; break;
                                case '"': val += '"'; break;
                                case '\\': val += '\\'; break;
                                default: val += next;
                            }
                            i++;
                        } else if (line[i] == '"') {
                            break;
                        } else {
                            val += line[i];
                        }
                    }
                    parts.push_back(val);
                }
            }
        }

        if (parts.empty()) return "";
        // 拼接所有找到的字段
        std::string result;
        for (size_t i = 0; i < parts.size(); i++) {
            if (i > 0) result += "\n";
            result += parts[i];
        }
        return result;
    }

    static std::vector<std::string> load_json_like(const std::string& path, bool line_by_line) {
        std::vector<std::string> texts;
        std::ifstream file(path);
        if (!file.is_open()) throw std::runtime_error("Cannot open: " + path);

        std::string line;
        while (std::getline(file, line)) {
            if (line.empty()) continue;
            auto text = extract_text_from_json_line(line);
            if (!text.empty()) texts.push_back(text);
            if (!line_by_line) break;  // .json 只读第一行（假设是数组的第一个元素）
        }

        // 如果 JSONL 方式没解析出东西，当纯文本读
        if (texts.empty()) {
            file.clear();
            file.seekg(0);
            std::string all((std::istreambuf_iterator<char>(file)),
                             std::istreambuf_iterator<char>());
            if (!all.empty()) texts.push_back(all);
        }

        return texts;
    }

    static std::vector<std::string> load_csv(const std::string& path) {
        std::vector<std::string> texts;
        std::ifstream file(path);
        if (!file.is_open()) throw std::runtime_error("Cannot open: " + path);

        std::string line;
        bool first = true;
        while (std::getline(file, line)) {
            if (first) { first = false; continue; }  // 跳表头
            if (line.empty()) continue;
            // 简单处理：把整行当一个样本
            texts.push_back(line);
        }
        return texts;
    }

    static std::vector<std::string> load_plain_text(const std::string& path) {
        std::vector<std::string> texts;
        std::ifstream file(path);
        if (!file.is_open()) throw std::runtime_error("Cannot open: " + path);

        std::string all((std::istreambuf_iterator<char>(file)),
                         std::istreambuf_iterator<char>());

        // 按双换行分段
        std::string segment;
        for (size_t i = 0; i < all.size(); i++) {
            if (all[i] == '\n' && i + 1 < all.size() && all[i + 1] == '\n') {
                if (!segment.empty()) {
                    texts.push_back(segment);
                    segment.clear();
                }
                i++;  // skip second newline
            } else {
                segment += all[i];
            }
        }
        if (!segment.empty()) texts.push_back(segment);

        // 如果没有分段（全文没有双换行），按单换行分
        if (texts.size() <= 1 && all.find('\n') != std::string::npos) {
            texts.clear();
            std::istringstream iss(all);
            std::string line;
            while (std::getline(iss, line)) {
                if (!line.empty()) texts.push_back(line);
            }
        }

        // 还是只有一个段，就整篇当一个样本
        if (texts.empty() && !all.empty()) texts.push_back(all);

        return texts;
    }
};

}  // namespace pocket_trainer
