#pragma once
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <random>
#include <algorithm>
#include <stdexcept>
#include <cstdint>
#include "cJSON.h"
#include "bpe_tokenizer.h"

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
    TextDataset(const std::string& path, int seq_len,
                 const std::string& system_prompt = "",
                 int seed = 42,
                 BpeTokenizer* tokenizer = nullptr)
        : seq_len_(seq_len), system_prompt_(system_prompt), tokenizer_(tokenizer)
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
    std::string system_prompt_;
    BpeTokenizer* tokenizer_;  // optional, not owned
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
        // 如果有系统提示词，先编码它作为前缀
        std::vector<int32_t> prompt_ids;
        if (!system_prompt_.empty()) {
            // UTF-8 解码系统提示词
            for (size_t i = 0; i < system_prompt_.size(); ) {
                unsigned char b = static_cast<unsigned char>(system_prompt_[i]);
                int32_t cp = 0;
                int bytes = 0;
                if (b < 0x80) { cp = b; bytes = 1; }
                else if ((b & 0xE0) == 0xC0) { cp = b & 0x1F; bytes = 2; }
                else if ((b & 0xF0) == 0xE0) { cp = b & 0x0F; bytes = 3; }
                else if ((b & 0xF8) == 0xF0) { cp = b & 0x07; bytes = 4; }
                else { bytes = 1; cp = b; }
                for (int j = 1; j < bytes && (i + j) < system_prompt_.size(); j++) {
                    unsigned char cont = static_cast<unsigned char>(system_prompt_[i + j]);
                    if ((cont & 0xC0) != 0x80) { bytes = j; break; }
                    cp = (cp << 6) | (cont & 0x3F);
                }
                i += bytes;
                prompt_ids.push_back(cp);
            }
        }

        for (const auto& text : texts) {
            // 每个样本前注入系统提示词
            token_ids_.insert(token_ids_.end(), prompt_ids.begin(), prompt_ids.end());
            // UTF-8 解码：按码点 tokenize，每个字符一个 token
            if (tokenizer_) {
                auto ids = tokenizer_->encode(text);
                token_ids_.insert(token_ids_.end(), ids.begin(), ids.end());
            } else {
                for (size_t i = 0; i < text.size(); ) {
                    unsigned char b = static_cast<unsigned char>(text[i]);
                    int32_t cp = 0;
                    int bytes = 0;
                    if (b < 0x80) { cp = b; bytes = 1; }
                    else if ((b & 0xE0) == 0xC0) { cp = b & 0x1F; bytes = 2; }
                    else if ((b & 0xF0) == 0xE0) { cp = b & 0x0F; bytes = 3; }
                    else if ((b & 0xF8) == 0xF0) { cp = b & 0x07; bytes = 4; }
                    else { bytes = 1; cp = b; } // invalid lead byte, treat as-is
                    for (int j = 1; j < bytes && (i + j) < text.size(); j++) {
                        unsigned char cont = static_cast<unsigned char>(text[i + j]);
                        if ((cont & 0xC0) != 0x80) { bytes = j; break; } // bad continuation
                        cp = (cp << 6) | (cont & 0x3F);
                    }
                    i += bytes;
                    token_ids_.push_back(cp);
                }
            }
            // 样本间加分隔
            token_ids_.push_back(0);  // null byte as separator
        }
    }

    static std::string extract_text_from_json_line(const std::string&line) {
        cJSON* j = cJSON_Parse(line.c_str());
        if (!j) return "";

        // 查扫默后的关键：text \"instruction" "input" "output" "content"
        const char* keys[] = {"text", "instruction", "input", "output", "content", "messages"};
        std::string result;
        for (int k = 0; k < 6; k++) {
            cJSON* item = cJSON_GetObjectItem(j, keys[k]);
            if (!item) continue;

            // 导入则主通当 "messages" 导入量
            if (cJSON_IsArray(item) && strcmp(keys[k], "messages") == 0) {
                cJSON* msg = item->child;
                while (msg) {
                    cJSON* content = cJSON_GetObjectItem(msg, "content");
                    if (content && cJSON_IsString(content)) {
                        if (!result.empty()) result += "\n";
                        result += cJSON_GetStringValue(content);
                    }
                    msg = msg->next;
                }
                cJSON_Delete(j);
                return result;
            }

            // 普通文本关键
            if (cJSON_IsString(item) && !result.empty()) result += "\n";
            if (cJSON_IsString(item)) result += cJSON_GetStringValue(item);
        }

        cJSON_Delete(j);
        return result;
    }}

    static std::vector<std::string> load_json_like(const std::string& path, bool line_by_line) {
        std::vector<std::string> texts;

        if (line_by_line) {
            // CSV/JSONL: read line by line
            std::ifstream file(path);
            if (!file.is_open()) throw std::runtime_error("Cannot open: " + path);
            std::string line;
            while (std::getline(file, line)) {
                if (line.empty()) continue;
                auto text = extract_text_from_json_line(line);
                if (!text.empty()) texts.push_back(text);
            }
        } else {
            // JSON: read entire file, parse array with cJSON
            std::ifstream file(path);
            if (!file.is_open()) throw std::runtime_error("Cannot open: " + path);
            std::string content((std::istreambuf_iterator<char>(file)),
                                std::istreambuf_iterator<char>());
            cJSON* root = cJSON_Parse(content.c_str());
            if (root) {
                if (cJSON_IsArray(root)) {
                    cJSON* item = root->child;
                    while (item) {
                        // Serialize each array element back to string, then extract text
                        char* item_str = cJSON_PrintUnformatted(item);
                        if (item_str) {
                            auto t = extract_text_from_json_line(std::string(item_str));
                            if (!t.empty()) texts.push_back(t);
                            cJSON_free(item_str);
                        }
                        item = item->next;
                    }
                } else {
                    // Single JSON object (not array)
                    char* obj_str = cJSON_PrintUnformatted(root);
                    if (obj_str) {
                        auto t = extract_text_from_json_line(std::string(obj_str));
                        if (!t.empty()) texts.push_back(t);
                        cJSON_free(obj_str);
                    }
                }
                cJSON_Delete(root);
            } else {
                // cJSON parse failed - fallback to plain text
                if (!content.empty()) texts.push_back(content);
            }
        }

        // Last resort: if nothing parsed, read as plain text
        if (texts.empty()) {
            std::ifstream file(path);
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
