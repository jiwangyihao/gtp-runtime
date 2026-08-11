package io.github.community.gtp.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DictionaryTranslatorTest {
    @Test
    public void exactMenuLabelsTranslateInStableOrder() {
        DictionaryTranslator translator = DictionaryTranslator.seedMenuDictionary();

        List<DictionaryTranslator.Match> matches =
                translator.translateLines(List.of("ホーム", "クエスト", "編成", "強化"));

        assertEquals(4, matches.size());
        assertEquals("主页", matches.get(0).translation());
        assertEquals("关卡", matches.get(1).translation());
        assertEquals("编队", matches.get(2).translation());
        assertEquals("强化", matches.get(3).translation());
    }

    @Test
    public void embeddedLabelsReturnOnlyKnownTranslations() {
        DictionaryTranslator translator = DictionaryTranslator.seedMenuDictionary();

        List<DictionaryTranslator.Match> matches =
                translator.translateLines(List.of("デイリーミッション 一括受取"));

        assertEquals(2, matches.size());
        assertEquals("任务", matches.get(0).translation());
        assertEquals("一键领取", matches.get(1).translation());
    }

    @Test
    public void longestEmbeddedTermWinsRegardlessOfInsertionOrder() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("受取", "领取");
        entries.put("一括受取", "一键领取");
        DictionaryTranslator translator = new DictionaryTranslator(entries);

        List<DictionaryTranslator.Match> matches =
                translator.translateLines(List.of("今すぐ一括受取"));

        assertEquals(1, matches.size());
        assertEquals("一括受取", matches.get(0).source());
        assertEquals("一键领取", matches.get(0).translation());
    }
    
    @Test
    public void ocrNoiseAfterKnownLabelIsNotEchoedAsTranslation() {
        DictionaryTranslator translator = DictionaryTranslator.seedMenuDictionary();

        List<DictionaryTranslator.Match> matches =
                translator.translateLines(List.of("ホーム →主"));

        assertEquals(1, matches.size());
        assertEquals("ホーム", matches.get(0).source());
        assertEquals("主页", matches.get(0).translation());
    }

    @Test
    public void unknownTextDoesNotProduceFakeTranslation() {
        DictionaryTranslator translator = DictionaryTranslator.seedMenuDictionary();

        assertTrue(translator.translateLines(List.of("クロエコ", "Rudeus")).isEmpty());
    }

    @Test
    public void duplicateTranslationsAreCollapsed() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("ランク", "等级");
        entries.put("レベル", "等级");
        DictionaryTranslator translator = new DictionaryTranslator(entries);

        List<DictionaryTranslator.Match> matches =
                translator.translateLines(List.of("ランク", "レベル"));

        assertEquals(1, matches.size());
        assertEquals("等级", matches.get(0).translation());
    }
}
