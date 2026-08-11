package io.github.community.gtp.overlay;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic offline translation for short game UI labels. */
final class DictionaryTranslator {
    static final int MAX_TRANSLATIONS_PER_FRAME = 10;

    record Match(String source, String translation) {}

    private final Map<String, String> exact;
    private final List<Map.Entry<String, String>> replacements;

    DictionaryTranslator(Map<String, String> entries) {
        exact = new LinkedHashMap<>();
        entries.forEach((source, translation) -> exact.put(normalize(source), translation));
        replacements = new ArrayList<>(exact.entrySet());
        replacements.sort(
                Comparator.<Map.Entry<String, String>>comparingInt(entry -> entry.getKey().length())
                        .reversed());
    }

    static DictionaryTranslator seedMenuDictionary() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("一括受取", "一键领取");
        entries.put("受け取る", "领取");
        entries.put("タイトルへ", "返回标题");
        entries.put("再挑戦", "再次挑战");
        entries.put("キャラクター", "角色");
        entries.put("ストーリー", "剧情");
        entries.put("プレゼント", "礼物");
        entries.put("ミッション", "任务");
        entries.put("お知らせ", "公告");
        entries.put("パーティー", "队伍");
        entries.put("おすすめ", "推荐");
        entries.put("キャンセル", "取消");
        entries.put("ホーム", "主页");
        entries.put("クエスト", "关卡");
        entries.put("ショップ", "商店");
        entries.put("アイテム", "道具");
        entries.put("パーティ", "队伍");
        entries.put("スキップ", "跳过");
        entries.put("オート", "自动");
        entries.put("メニュー", "菜单");
        entries.put("ガチャ", "召唤");
        entries.put("バトル", "战斗");
        entries.put("ランク", "等级");
        entries.put("レベル", "等级");
        entries.put("経験値", "经验值");
        entries.put("ログイン", "登录");
        entries.put("ダウンロード", "下载");
        entries.put("タップ", "点击");
        entries.put("編成", "编队");
        entries.put("強化", "强化");
        entries.put("育成", "培养");
        entries.put("召喚", "召唤");
        entries.put("装備", "装备");
        entries.put("出撃", "出战");
        entries.put("報酬", "奖励");
        entries.put("受取", "领取");
        entries.put("所持", "拥有");
        entries.put("交換", "兑换");
        entries.put("購入", "购买");
        entries.put("無料", "免费");
        entries.put("有償", "付费");
        entries.put("無償", "免费");
        entries.put("体力", "体力");
        entries.put("詳細", "详情");
        entries.put("確認", "确认");
        entries.put("設定", "设置");
        entries.put("戻る", "返回");
        entries.put("閉じる", "关闭");
        entries.put("決定", "确定");
        entries.put("次へ", "下一步");
        entries.put("開始", "开始");
        entries.put("続ける", "继续");
        entries.put("冒険", "冒险");
        return new DictionaryTranslator(entries);
    }

    List<Match> translateLines(List<String> lines) {
        List<Match> matches = new ArrayList<>();
        Set<String> seenTranslations = new LinkedHashSet<>();
        for (String rawLine : lines) {
            if (matches.size() >= MAX_TRANSLATIONS_PER_FRAME) {
                break;
            }
            String source = normalize(rawLine);
            if (source.isEmpty()) {
                continue;
            }
            String exactTranslation = exact.get(source);
            if (exactTranslation != null) {
                addMatch(matches, seenTranslations, source, exactTranslation);
                continue;
            }

            List<EmbeddedMatch> candidates = new ArrayList<>();
            for (Map.Entry<String, String> replacement : replacements) {
                String term = replacement.getKey();
                int start = source.indexOf(term);
                while (start >= 0) {
                    candidates.add(new EmbeddedMatch(
                            start,
                            term,
                            replacement.getValue()));
                    start = source.indexOf(term, start + 1);
                }
            }
            candidates.sort(
                    Comparator.<EmbeddedMatch>comparingInt(
                                    candidate -> candidate.source().length())
                            .reversed()
                            .thenComparingInt(EmbeddedMatch::start)
                            .thenComparing(EmbeddedMatch::source));

            boolean[] claimed = new boolean[source.length()];
            List<EmbeddedMatch> selected = new ArrayList<>();
            for (EmbeddedMatch candidate : candidates) {
                int end = candidate.start() + candidate.source().length();
                if (!isUnclaimed(claimed, candidate.start(), end)) {
                    continue;
                }
                for (int index = candidate.start(); index < end; index++) {
                    claimed[index] = true;
                }
                selected.add(candidate);
            }
            selected.sort(
                    Comparator.comparingInt(EmbeddedMatch::start)
                            .thenComparing(
                                    Comparator.comparingInt(
                                                    (EmbeddedMatch candidate) ->
                                                            candidate.source().length())
                                            .reversed()));
            for (EmbeddedMatch candidate : selected) {
                if (matches.size() >= MAX_TRANSLATIONS_PER_FRAME) {
                    break;
                }
                addMatch(
                        matches,
                        seenTranslations,
                        candidate.source(),
                        candidate.translation());
            }
        }
        return matches;
    }

    private static boolean isUnclaimed(boolean[] claimed, int start, int end) {
        for (int index = start; index < end; index++) {
            if (claimed[index]) {
                return false;
            }
        }
        return true;
    }

    private static void addMatch(
            List<Match> matches,
            Set<String> seenTranslations,
            String source,
            String translation) {
        if (seenTranslations.add(translation)) {
            matches.add(new Match(source, translation));
        }
    }

    private record EmbeddedMatch(int start, String source, String translation) {}


    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u3000', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }
}
