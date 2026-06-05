#pragma once
#include <string>
#include <vector>
#include <unordered_map>
#include <map>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cstdint>
#include <climits>
#include "cJSON.h"

class BpeTokenizer {
public:
    BpeTokenizer() = default;

    bool load(const std::string& vocab_path, const std::string& merges_path) {
        if (!load_vocab(vocab_path)) return false;
        if (!load_merges(merges_path)) return false;
        build_byte_encoder();
        return true;
    }

    std::vector<int32_t> encode(const std::string& text) const {
        std::vector<int32_t> ids;
        auto words = pre_tokenize(text);
        for (const auto& word : words) {
            auto tokens = bpe_encode_word(word);
            for (const auto& t : tokens) {
                auto it = vocab_.find(t);
                if (it != vocab_.end()) {
                    ids.push_back(it->second);
                } else {
                    for (unsigned char c : t) {
                        auto bit = byte_encoder_.find(c);
                        if (bit != byte_encoder_.end()) {
                            auto vit = vocab_.find(bit->second);
                            if (vit != vocab_.end()) ids.push_back(vit->second);
                        }
                    }
                }
            }
        }
        return ids;
    }

    std::string decode(const std::vector<int32_t>& ids) const {
        std::string result;
        for (int32_t id : ids) {
            if (id >= 0 && id < (int32_t)inv_vocab_.size()) {
                const std::string& token = inv_vocab_[id];
                for (size_t i = 0; i < token.size(); ) {
                    unsigned char ch = token[i];
                    int len = utf8_len(ch);
                    std::string cp = token.substr(i, len);
                    auto it = byte_decoder_.find(cp);
                    if (it != byte_decoder_.end()) {
                        result += (char)it->second;
                    } else {
                        result += cp;
                    }
                    i += len;
                }
            }
        }
        return result;
    }

    int vocab_size() const { return (int)vocab_.size(); }

private:
    std::unordered_map<std::string, int32_t> vocab_;
    std::vector<std::string> inv_vocab_;
    std::map<std::pair<std::string,std::string>, int> bpe_ranks_;
    std::unordered_map<unsigned char, std::string> byte_encoder_;
    std::unordered_map<std::string, unsigned char> byte_decoder_;

    static int utf8_len(unsigned char c) {
        if (c < 0x80) return 1;
        if (c < 0xE0) return 2;
        if (c < 0xF0) return 3;
        return 4;
    }

    void build_byte_encoder() {
        std::vector<int> bs;
        for (int i = 33; i <= 126; i++) bs.push_back(i);
        for (int i = 161; i <= 172; i++) bs.push_back(i);
        for (int i = 174; i <= 255; i++) bs.push_back(i);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            bool found = false;
            for (int x : bs) if (x == b) { found = true; break; }
            if (!found) bs.push_back(b);
        }
        for (int b : bs) {
            bool is_printable = (b >= 33 && b <= 126) ||
                               (b >= 161 && b <= 172) ||
                               (b >= 174 && b <= 255);
            int cp = is_printable ? b : (256 + n++);
            std::string s = encode_utf8(cp);
            byte_encoder_[(unsigned char)b] = s;
            byte_decoder_[s] = (unsigned char)b;
        }
    }

    static std::string encode_utf8(int cp) {
        std::string s;
        if (cp < 0x80) {
            s += (char)cp;
        } else if (cp < 0x800) {
            s += (char)(0xC0 | (cp >> 6));
            s += (char)(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            s += (char)(0xE0 | (cp >> 12));
            s += (char)(0x80 | ((cp >> 6) & 0x3F));
            s += (char)(0x80 | (cp & 0x3F));
        } else {
            s += (char)(0xF0 | (cp >> 18));
            s += (char)(0x80 | ((cp >> 12) & 0x3F));
            s += (char)(0x80 | ((cp >> 6) & 0x3F));
            s += (char)(0x80 | (cp & 0x3F));
        }
        return s;
    }

    bool load_vocab(const std::string& path) {
        std::ifstream f(path);
        if (!f.is_open()) return false;
        std::string content((std::istreambuf_iterator<char>(f)),
                             std::istreambuf_iterator<char>());
        cJSON* root = cJSON_Parse(content.c_str());
        if (!root || !cJSON_IsObject(root)) { cJSON_Delete(root); return false; }
        cJSON* item = root->child;
        int max_id = 0;
        while (item) {
            if (cJSON_IsNumber(item)) {
                vocab_[item->string] = item->valueint;
                if (item->valueint > max_id) max_id = item->valueint;
            }
            item = item->next;
        }
        cJSON_Delete(root);
        inv_vocab_.resize(max_id + 1);
        for (auto& kv : vocab_) inv_vocab_[kv.second] = kv.first;
        return true;
    }

    bool load_merges(const std::string& path) {
        std::ifstream f(path);
        if (!f.is_open()) return false;
        std::string line;
        int rank = 0;
        while (std::getline(f, line)) {
            if (line.empty() || line[0] == '#') continue;
            auto sp = line.find(' ');
            if (sp == std::string::npos) continue;
            std::string first = line.substr(0, sp);
            std::string second = line.substr(sp + 1);
            bpe_ranks_[{first, second}] = rank++;
        }
        return true;
    }

    std::vector<std::string> pre_tokenize(const std::string& text) const {
        std::vector<std::string> words;
        std::string current;
        for (size_t i = 0; i < text.size(); ) {
            unsigned char c = text[i];
            int len = utf8_len(c);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                if (!current.empty()) {
                    words.push_back(current);
                    current.clear();
                }
                if (c == ' ') current += ' ';
                i++;
            } else {
                current += text.substr(i, len);
                i += len;
            }
        }
        if (!current.empty()) words.push_back(current);
        return words;
    }

    std::vector<std::string> bpe_encode_word(const std::string& word) const {
        std::vector<std::string> symbols;
        for (unsigned char c : word) {
            auto it = byte_encoder_.find(c);
            if (it != byte_encoder_.end()) {
                symbols.push_back(it->second);
            }
        }
        if (symbols.size() <= 1) return symbols;

        while (symbols.size() > 1) {
            int best_rank = INT_MAX;
            int best_i = -1;
            for (int i = 0; i + 1 < (int)symbols.size(); i++) {
                auto it = bpe_ranks_.find({symbols[i], symbols[i+1]});
                if (it != bpe_ranks_.end() && it->second < best_rank) {
                    best_rank = it->second;
                    best_i = i;
                }
            }
            if (best_i < 0) break;
            symbols[best_i] = symbols[best_i] + symbols[best_i+1];
            symbols.erase(symbols.begin() + best_i + 1);
        }
        return symbols;
    }
};
