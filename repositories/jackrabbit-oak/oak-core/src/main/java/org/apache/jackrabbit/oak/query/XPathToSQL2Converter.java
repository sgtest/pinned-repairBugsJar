/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.query;

import org.apache.jackrabbit.oak.commons.PathUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;

/**
 * This class can can convert a XPATH query to a SQL2 query.
 */
public class XPathToSQL2Converter {

    // Character types, used during the tokenizer phase
    private static final int CHAR_END = -1, CHAR_VALUE = 2;
    private static final int CHAR_NAME = 4, CHAR_SPECIAL_1 = 5, CHAR_SPECIAL_2 = 6;
    private static final int CHAR_STRING = 7, CHAR_DECIMAL = 8;

    // Token types
    private static final int KEYWORD = 1, IDENTIFIER = 2, END = 4, VALUE_STRING = 5, VALUE_NUMBER = 6;
    private static final int MINUS = 12, PLUS = 13, OPEN = 14, CLOSE = 15;

    // The query as an array of characters and character types
    private String statement;
    private char[] statementChars;
    private int[] characterTypes;

    // The current state of the parser
    private int parseIndex;
    private int currentTokenType;
    private String currentToken;
    private boolean currentTokenQuoted;
    private ArrayList<String> expected;

    /**
     * Convert the query to SQL2.
     *
     * @param query the query string
     * @return the SQL2 query
     * @throws ParseException if parsing fails
     */
    public String convert(String query) throws ParseException {
        // TODO verify this is correct
        if (!query.startsWith("/")) {
            query = "/jcr:root/" + query;
        }
        initialize(query);
        expected = new ArrayList<String>();
        read();
        String path = "";
        Expression condition = null;
        String from = "nt:base";
        ArrayList<Expression> columnList = new ArrayList<Expression>();
        boolean children = true;
        boolean descendants = true;
        while (true) {
            String nodeType;
            if (readIf("/")) {
                if (readIf("/")) {
                    descendants = true;
                } else if (readIf("jcr:root")) {
                    path = "/";
                    if (readIf("/")) {
                        if (readIf("/")) {
                            descendants = true;
                        }
                    } else {
                        // expected end of statement
                        break;
                    }
                } else {
                    descendants = false;
                }
                if (readIf("*")) {
                    nodeType = "nt:base";
                    from = nodeType;
                } else if (readIf("text")) {
                    // TODO support text() and jcr:xmltext?
                    read("(");
                    read(")");
                    path = PathUtils.concat(path, "jcr:xmltext");
                } else if (readIf("element")) {
                    read("(");
                    if (readIf(")")) {
                        // any
                        children = true;
                    } else {
                        if (readIf("*")) {
                            // any
                            children = true;
                        } else {
                            children = false;
                            String name = readIdentifier();
                            path = PathUtils.concat(path, name);
                        }
                        if (readIf(",")) {
                            nodeType = readIdentifier();
                            from = nodeType;
                        } else {
                            nodeType = "nt:base";
                            from = nodeType;
                        }
                        read(")");
                    }
                } else if (readIf("@")) {
                    Property p = new Property(readIdentifier());
                    columnList.add(p);
                } else if (readIf("(")) {
                    do {
                        read("@");
                        Property p = new Property(readIdentifier());
                        columnList.add(p);
                    } while (readIf("|"));
                    read(")");
                } else {
                    String name = readIdentifier();
                    path = PathUtils.concat(path, name);
                    continue;
                }
                if (readIf("[")) {
                    Expression c = parseConstraint();
                    condition = add(condition, c);
                    read("]");
                }
            } else {
                break;
            }
        }
        if (path.isEmpty()) {
            // no condition
        } else {
            if (descendants) {
                Function c = new Function("isdescendantnode");
                c.params.add(Literal.newString(path));
                condition = add(condition, c);
            } else if (children) {
                Function f = new Function("ischildnode");
                f.params.add(Literal.newString(path));
                condition = add(condition, f);
            } else {
                // TODO jcr:path is only a pseudo-property
                Condition c = new Condition(new Property("jcr:path"), "=", Literal.newString(path));
                condition = add(condition, c);
            }
        }
        ArrayList<Order> orderList = new ArrayList<Order>();
        if (readIf("order")) {
            read("by");
            do {
                Order order = new Order();
                order.expr = parseExpression();
                if (readIf("descending")) {
                    order.descending = true;
                } else {
                    readIf("ascending");
                }
                orderList.add(order);
            } while (readIf(","));
        }
        if (!currentToken.isEmpty()) {
            throw getSyntaxError("<end>");
        }
        StringBuilder buff = new StringBuilder("select ");
        buff.append("[jcr:path], ");
        if (columnList.isEmpty()) {
            buff.append('*');
        } else {
            for (int i = 0; i < columnList.size(); i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                buff.append(columnList.get(i).toString());
            }
        }
        buff.append(" from ");
        buff.append('[' + from + ']');
        if (condition != null) {
            buff.append(" where ").append(removeParens(condition.toString()));
        }
        if (!orderList.isEmpty()) {
            buff.append(" order by ");
            for (int i = 0; i < orderList.size(); i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                buff.append(orderList.get(i));
            }
        }
        return buff.toString();
    }

    private static Expression add(Expression old, Expression add) {
        if (old == null) {
            return add;
        }
        return new Condition(old, "and", add);
    }

    private Expression parseConstraint() throws ParseException {
        Expression a = parseAnd();
        while (readIf("or")) {
            a = new Condition(a, "or", parseAnd());
        }
        return a;
    }

    private Expression parseAnd() throws ParseException {
        Expression a = parseCondition();
        while (readIf("and")) {
            a = new Condition(a, "and", parseCondition());
        }
        return a;
    }

    private Expression parseCondition() throws ParseException {
        Expression a;
        if (readIf("not")) {
            read("(");
            a = parseConstraint();
            if (a instanceof Condition && ((Condition) a).operator.equals("is not null")) {
                // not(@property) -> @property is null
                Condition c = (Condition) a;
                c = new Condition(c.left, "is null", null);
                a = c;
            } else {
                Function f = new Function("not");
                f.params.add(a);
                a = f;
            }
            read(")");
        } else if (readIf("(")) {
            a = parseConstraint();
            read(")");
        } else {
            Expression e = parseExpression();
            if (e.isCondition()) {
                return e;
            }
            a = parseCondition(e);
        }
        return a;
    }

    private Condition parseCondition(Expression left) throws ParseException {
        Condition c;
        if (readIf("=")) {
            c = new Condition(left, "=", parseExpression());
        } else if (readIf("<>")) {
            c = new Condition(left, "<>", parseExpression());
        } else if (readIf("<")) {
            c = new Condition(left, "<", parseExpression());
        } else if (readIf(">")) {
            c = new Condition(left, ">", parseExpression());
        } else if (readIf("<=")) {
            c = new Condition(left, "<=", parseExpression());
        } else if (readIf(">=")) {
            c = new Condition(left, ">=", parseExpression());
        } else {
            c = new Condition(left, "is not null", null);
        }
        return c;
    }

    private Expression parseExpression() throws ParseException {
        if (readIf("@")) {
            return new Property(readIdentifier());
        } else if (readIf("true")) {
            return Literal.newBoolean(true);
        } else if (readIf("false")) {
            return Literal.newBoolean(false);
        } else if (currentTokenType == VALUE_NUMBER) {
            Literal l = Literal.newNumber(currentToken);
            read();
            return l;
        } else if (currentTokenType == VALUE_STRING) {
            Literal l = Literal.newString(currentToken);
            read();
            return l;
        } else if (currentTokenType == IDENTIFIER) {
            String name = readIdentifier();
            // relative properties
            if (readIf("/")) {
                do {
                    if (readIf("@")) {
                        name = name + "/" + readIdentifier();
                        return new Property(name);
                    } else {
                        name = name + "/" + readIdentifier();
                    }
                } while (readIf("/"));
            }
            read("(");
            return parseFunction(name);
        } else if (readIf("-")) {
            if (currentTokenType != VALUE_NUMBER) {
                throw getSyntaxError();
            }
            Literal l = Literal.newNumber('-' + currentToken);
            read();
            return l;
        } else if (readIf("+")) {
            if (currentTokenType != VALUE_NUMBER) {
                throw getSyntaxError();
            }
            return parseExpression();
        } else {
            throw getSyntaxError();
        }
    }

    private Expression parseFunction(String functionName) throws ParseException {
        if ("jcr:like".equals(functionName)) {
            Condition c = new Condition(parseExpression(), "like", null);
            read(",");
            c.right = parseExpression();
            read(")");
            return c;
        } else if ("jcr:contains".equals(functionName)) {
            Function f = new Function("contains");
            if (readIf(".")) {
                // special case: jcr:contains(., expr)
                f.params.add(new Literal("*"));
            } else {
                f.params.add(parseExpression());
            }
            read(",");
            f.params.add(parseExpression());
            read(")");
            return f;
        } else if ("jcr:score".equals(functionName)) {
            Function f = new Function("score");
            // TODO score: support parameters?
            read(")");
            return f;
        } else if ("xs:dateTime".equals(functionName)) {
            Expression expr = parseExpression();
            Cast c = new Cast(expr, "date");
            read(")");
            return c;
        // } else if ("jcr:deref".equals(functionName)) {
            // TODO support jcr:deref?
        } else {
            throw getSyntaxError("jcr:like | jcr:contains | jcr:score | jcr:deref");
        }
    }

    private boolean readIf(String token) throws ParseException {
        if (isToken(token)) {
            read();
            return true;
        }
        return false;
    }

    private boolean isToken(String token) {
        boolean result = token.equals(currentToken) && !currentTokenQuoted;
        if (result) {
            return true;
        }
        addExpected(token);
        return false;
    }

    private void read(String expected) throws ParseException {
        if (!expected.equals(currentToken) || currentTokenQuoted) {
            throw getSyntaxError(expected);
        }
        read();
    }

    private String readIdentifier() throws ParseException {
        if (currentTokenType != IDENTIFIER) {
            throw getSyntaxError("identifier");
        }
        String s = currentToken;
        read();
        return s;
    }

    private void addExpected(String token) {
        if (expected != null) {
            expected.add(token);
        }
    }

    private void initialize(String query) throws ParseException {
        if (query == null) {
            query = "";
        }
        statement = query;
        int len = query.length() + 1;
        char[] command = new char[len];
        int[] types = new int[len];
        len--;
        query.getChars(0, len, command, 0);
        command[len] = ' ';
        int startLoop = 0;
        for (int i = 0; i < len; i++) {
            char c = command[i];
            int type = 0;
            switch (c) {
            case '@':
            case '|':
            case '/':
            case '-':
            case '(':
            case ')':
            case '{':
            case '}':
            case '*':
            case ',':
            case ';':
            case '+':
            case '%':
            case '?':
            case '$':
            case '[':
            case ']':
                type = CHAR_SPECIAL_1;
                break;
            case '!':
            case '<':
            case '>':
            case '=':
                type = CHAR_SPECIAL_2;
                break;
            case '.':
                type = CHAR_DECIMAL;
                break;
            case '\'':
                type = CHAR_STRING;
                types[i] = CHAR_STRING;
                startLoop = i;
                while (command[++i] != '\'') {
                    checkRunOver(i, len, startLoop);
                }
                break;
            case ':':
            case '_':
                type = CHAR_NAME;
                break;
            default:
                if (c >= 'a' && c <= 'z') {
                    type = CHAR_NAME;
                } else if (c >= 'A' && c <= 'Z') {
                    type = CHAR_NAME;
                } else if (c >= '0' && c <= '9') {
                    type = CHAR_VALUE;
                } else {
                    if (Character.isJavaIdentifierPart(c)) {
                        type = CHAR_NAME;
                    }
                }
            }
            types[i] = (byte) type;
        }
        statementChars = command;
        types[len] = CHAR_END;
        characterTypes = types;
        parseIndex = 0;
    }

    private void checkRunOver(int i, int len, int startLoop) throws ParseException {
        if (i >= len) {
            parseIndex = startLoop;
            throw getSyntaxError();
        }
    }

    private void read() throws ParseException {
        currentTokenQuoted = false;
        if (expected != null) {
            expected.clear();
        }
        int[] types = characterTypes;
        int i = parseIndex;
        int type = types[i];
        while (type == 0) {
            type = types[++i];
        }
        int start = i;
        char[] chars = statementChars;
        char c = chars[i++];
        currentToken = "";
        switch (type) {
        case CHAR_NAME:
            while (true) {
                type = types[i];
                if (type != CHAR_NAME && type != CHAR_VALUE) {
                    c = chars[i];
                    break;
                }
                i++;
            }
            currentToken = statement.substring(start, i);
            if (currentToken.isEmpty()) {
                throw getSyntaxError();
            }
            currentTokenType = IDENTIFIER;
            parseIndex = i;
            return;
        case CHAR_SPECIAL_2:
            if (types[i] == CHAR_SPECIAL_2) {
                i++;
            }
            // fall through
        case CHAR_SPECIAL_1:
            currentToken = statement.substring(start, i);
            switch (c) {
            case '+':
                currentTokenType = PLUS;
                break;
            case '-':
                currentTokenType = MINUS;
                break;
            case '(':
                currentTokenType = OPEN;
                break;
            case ')':
                currentTokenType = CLOSE;
                break;
            default:
                currentTokenType = KEYWORD;
            }
            parseIndex = i;
            return;
        case CHAR_VALUE:
            long number = c - '0';
            while (true) {
                c = chars[i];
                if (c < '0' || c > '9') {
                    if (c == '.') {
                        readDecimal(start, i);
                        break;
                    }
                    if (c == 'E' || c == 'e') {
                        readDecimal(start, i);
                        break;
                    }
                    currentTokenType = VALUE_NUMBER;
                    currentToken = String.valueOf(number);
                    parseIndex = i;
                    break;
                }
                number = number * 10 + (c - '0');
                if (number > Integer.MAX_VALUE) {
                    readDecimal(start, i);
                    break;
                }
                i++;
            }
            return;
        case CHAR_DECIMAL:
            if (types[i] != CHAR_VALUE) {
                currentTokenType = KEYWORD;
                currentToken = ".";
                parseIndex = i;
                return;
            }
            readDecimal(i - 1, i);
            return;
        case CHAR_STRING:
            readString(i, '\'');
            return;
        case CHAR_END:
            currentToken = "";
            currentTokenType = END;
            parseIndex = i;
            return;
        default:
            throw getSyntaxError();
        }
    }

    private void readString(int i, char end) {
        char[] chars = statementChars;
        String result = null;
        while (true) {
            for (int begin = i;; i++) {
                if (chars[i] == end) {
                    if (result == null) {
                        result = statement.substring(begin, i);
                    } else {
                        result += statement.substring(begin - 1, i);
                    }
                    break;
                }
            }
            if (chars[++i] != end) {
                break;
            }
            i++;
        }
        currentToken = result;
        parseIndex = i;
        currentTokenType = VALUE_STRING;
    }

    private void readDecimal(int start, int i) throws ParseException {
        char[] chars = statementChars;
        int[] types = characterTypes;
        while (true) {
            int t = types[i];
            if (t != CHAR_DECIMAL && t != CHAR_VALUE) {
                break;
            }
            i++;
        }
        if (chars[i] == 'E' || chars[i] == 'e') {
            i++;
            if (chars[i] == '+' || chars[i] == '-') {
                i++;
            }
            if (types[i] != CHAR_VALUE) {
                throw getSyntaxError();
            }
            while (types[++i] == CHAR_VALUE) {
                // go until the first non-number
            }
        }
        parseIndex = i;
        String sub = statement.substring(start, i);
        try {
            new BigDecimal(sub);
        } catch (NumberFormatException e) {
            throw new ParseException("Data conversion error converting " + sub + " to BigDecimal: " + e, i);
        }
        currentToken = sub;
        currentTokenType = VALUE_NUMBER;
    }

    private ParseException getSyntaxError() {
        if (expected == null || expected.isEmpty()) {
            return getSyntaxError(null);
        } else {
            StringBuilder buff = new StringBuilder();
            for (String exp : expected) {
                if (buff.length() > 0) {
                    buff.append(", ");
                }
                buff.append(exp);
            }
            return getSyntaxError(buff.toString());
        }
    }

    private ParseException getSyntaxError(String expected) {
        int index = Math.min(parseIndex, statement.length() - 1);
        String query = statement.substring(0, index) + "(*)" + statement.substring(index).trim();
        if (expected != null) {
            query += "; expected: " + expected;
        }
        return new ParseException("Query:\n" + query, index);
    }

    abstract static class Expression {

        boolean isCondition() {
            return false;
        }

    }

    static class Literal extends Expression {

        final String value;

        Literal(String value) {
            this.value = value;
        }

        public static Expression newBoolean(boolean value) {
            return new Literal(String.valueOf(value));
        }

        static Literal newNumber(String s) {
            return new Literal(s);
        }

        static Literal newString(String s) {
            return new Literal(SQL2Parser.escapeStringLiteral(s));
        }

        @Override
        public String toString() {
            return value;
        }

    }

    static class Property extends Expression {

        final String name;

        Property(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return '[' + name + ']';
        }

    }

    static class Condition extends Expression {

        final Expression left;
        final String operator;
        Expression right;

        Condition(Expression left, String operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public String toString() {
            return
                "(" +
                (left == null ? "" : left + " ") +
                operator +
                (right == null ? "" : " " + right) +
                ")";
        }

        @Override
        boolean isCondition() {
            return true;
        }

    }

    static class Function extends Expression {

        final String name;
        final ArrayList<Expression> params = new ArrayList<Expression>();

        Function(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            StringBuilder buff = new StringBuilder(name);
            buff.append('(');
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                buff.append(removeParens(params.get(i).toString()));
            }
            buff.append(')');
            return buff.toString();
        }

        @Override
        boolean isCondition() {
            return name.equals("contains") || name.equals("not");
        }

    }

    static class Cast extends Expression {

        final Expression expr;
        final String type;

        Cast(Expression expr, String type) {
            this.expr = expr;
            this.type = type;
        }

        @Override
        public String toString() {
            StringBuilder buff = new StringBuilder("cast(");
            buff.append(removeParens(expr.toString()));
            buff.append(" as ").append(type).append(')');
            return buff.toString();
        }

        @Override
        boolean isCondition() {
            return false;
        }

    }

    static class Order {

        boolean descending;
        Expression expr;

        @Override
        public String toString() {
            return expr + (descending ? " desc" : "");
        }

    }

    static String removeParens(String s) {
        if (s.startsWith("(") && s.endsWith(")")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

}

