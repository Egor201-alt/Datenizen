package com.egor201.datenizen.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SqlUtilsTest {

    // --- parseCsvLine ---

    @Test
    void csv_simpleFields() {
        List<String> result = SqlUtils.parseCsvLine("a,b,c");
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void csv_quotedFieldWithComma() {
        List<String> result = SqlUtils.parseCsvLine("\"hello, world\",b");
        assertEquals(List.of("hello, world", "b"), result);
    }

    @Test
    void csv_escapedQuoteInsideField() {
        List<String> result = SqlUtils.parseCsvLine("\"say \"\"hi\"\"\",b");
        assertEquals(List.of("say \"hi\"", "b"), result);
    }

    @Test
    void csv_emptyMiddleField() {
        List<String> result = SqlUtils.parseCsvLine("a,,c");
        assertEquals(List.of("a", "", "c"), result);
    }

    @Test
    void csv_trailingEmpty() {
        List<String> result = SqlUtils.parseCsvLine("a,b,");
        assertEquals(List.of("a", "b", ""), result);
    }

    @Test
    void csv_singleUnquotedField() {
        List<String> result = SqlUtils.parseCsvLine("hello");
        assertEquals(List.of("hello"), result);
    }

    @Test
    void csv_spacesAroundUnquotedTrimmed() {
        List<String> result = SqlUtils.parseCsvLine(" a , b ");
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void csv_quotedFieldSpacesNotTrimmed() {
        List<String> result = SqlUtils.parseCsvLine("\" hello \"");
        assertEquals(List.of(" hello "), result);
    }

    // --- splitStatements ---

    @Test
    void split_singleStatement() {
        List<String> result = SqlUtils.splitStatements("SELECT 1");
        assertEquals(List.of("SELECT 1"), result);
    }

    @Test
    void split_twoStatements() {
        List<String> result = SqlUtils.splitStatements("SELECT 1; SELECT 2");
        assertEquals(List.of("SELECT 1", "SELECT 2"), result);
    }

    @Test
    void split_trailingSemicolon() {
        List<String> result = SqlUtils.splitStatements("SELECT 1;");
        assertEquals(List.of("SELECT 1"), result);
    }

    @Test
    void split_semicolonInSingleQuote() {
        List<String> result = SqlUtils.splitStatements("INSERT INTO t VALUES ('a;b')");
        assertEquals(List.of("INSERT INTO t VALUES ('a;b')"), result);
    }

    @Test
    void split_semicolonInDoubleQuote() {
        List<String> result = SqlUtils.splitStatements("SELECT \"col;name\" FROM t");
        assertEquals(List.of("SELECT \"col;name\" FROM t"), result);
    }

    @Test
    void split_lineCommentSemicolonIgnored() {
        List<String> result = SqlUtils.splitStatements("SELECT 1 -- semicolon; here\n; SELECT 2");
        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("-- semicolon; here"));
        assertEquals("SELECT 2", result.get(1));
    }

    @Test
    void split_blockCommentSemicolonIgnored() {
        List<String> result = SqlUtils.splitStatements("SELECT 1 /* a;b */ FROM t; SELECT 2");
        assertEquals(List.of("SELECT 1 /* a;b */ FROM t", "SELECT 2"), result);
    }

    @Test
    void split_emptyInput() {
        List<String> result = SqlUtils.splitStatements("");
        assertTrue(result.isEmpty());
    }

    @Test
    void split_whitespaceOnlyBetweenSemicolons() {
        List<String> result = SqlUtils.splitStatements("SELECT 1;  ;SELECT 2");
        assertEquals(List.of("SELECT 1", "SELECT 2"), result);
    }

    // --- SAFE_NAME pattern ---

    @Test
    void safeName_valid() {
        Pattern p = Pattern.compile("^[a-zA-Z0-9_]+$");
        assertTrue(p.matcher("players").matches());
        assertTrue(p.matcher("player_data").matches());
        assertTrue(p.matcher("Table123").matches());
        assertTrue(p.matcher("_private").matches());
    }

    @Test
    void safeName_invalid() {
        Pattern p = Pattern.compile("^[a-zA-Z0-9_]+$");
        assertFalse(p.matcher("").matches());
        assertFalse(p.matcher("drop;table").matches());
        assertFalse(p.matcher("a b").matches());
        assertFalse(p.matcher("a-b").matches());
        assertFalse(p.matcher("../etc").matches());
    }
}
