"""查询规范化和正式中文分词入口。"""

import re
import unicodedata

try:
    import jieba
except ImportError:  # pragma: no cover - 依赖安装后由运行环境提供
    jieba = None


_SEPARATOR_RE = re.compile(r"[\s,，。；;、/／\\|:：!?！？（）()【】\[\]“”\"']+")


def normalize_query(value: object) -> str:
    text = unicodedata.normalize("NFKC", "" if value is None else str(value)).lower()
    text = _SEPARATOR_RE.sub(" ", text)
    return re.sub(r"\s+", " ", text).strip()


def configure_dictionary(words):
    if jieba is None:
        raise RuntimeError("缺少 jieba；中文 BM25 禁止使用字符 n-gram 代替，请先安装 requirements.txt")
    for word in words:
        if word and len(word.strip()) > 1:
            jieba.add_word(word.strip())


def tokenize_search_text(value: object):
    if jieba is None:
        raise RuntimeError("缺少 jieba；中文 BM25 禁止使用字符 n-gram 代替，请先安装 requirements.txt")
    text = normalize_query(value)
    tokens = []
    for token in jieba.lcut_for_search(text, HMM=True):
        normalized = normalize_query(token)
        if normalized:
            tokens.extend(part for part in normalized.split(" ") if part)
    return tokens
