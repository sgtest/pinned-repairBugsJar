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
package org.apache.jackrabbit.oak.namepath;

public class JcrPathParser {

    // constants for parser
    private static final int STATE_PREFIX_START = 0;
    private static final int STATE_PREFIX = 1;
    private static final int STATE_NAME_START = 2;
    private static final int STATE_NAME = 3;
    private static final int STATE_INDEX = 4;
    private static final int STATE_INDEX_END = 5;
    private static final int STATE_DOT = 6;
    private static final int STATE_DOTDOT = 7;
    private static final int STATE_IDENTIFIER = 8;
    private static final int STATE_URI = 9;
    private static final int STATE_URI_END = 10;

    private static final char EOF = (char) -1;

    private JcrPathParser() {
    }

    interface Listener extends JcrNameParser.Listener {
        boolean root();
        boolean identifier(String identifier);
        boolean current();
        boolean parent();
        boolean index(int index);
    }

    public static void parse(String jcrPath, Listener listener) {
        // check for length
        int len = jcrPath == null ? 0 : jcrPath.length();

        // shortcut
        if (len == 1 && jcrPath.charAt(0) == '/') {
            listener.root();
            return;
        }

        if (len == 0) {
            listener.error("empty path");
            return;
        }

        // check if absolute path
        int pos = 0;
        if (jcrPath.charAt(0) == '/') {
            if (!listener.root()) {
                return;
            }
            pos++;
        }

        // parse the path
        int state;
        if (jcrPath.charAt(0) == '[') {
            state = STATE_IDENTIFIER;
            pos++;
        } else {
            state = STATE_PREFIX_START;
        }

        int lastPos = pos;
        String name = null;

        int index = 0;
        boolean wasSlash = false;

        while (pos <= len) {
            char c = pos == len ? EOF : jcrPath.charAt(pos);
            pos++;
            // special check for whitespace
            if (c != ' ' && Character.isWhitespace(c)) {
                c = '\t';
            }
            
            switch (c) {
                case '/':
                case EOF:
                    if (state == STATE_PREFIX_START && c != EOF) {
                        listener.error('\'' + jcrPath + "' is not a valid path. " +
                                "double slash '//' not allowed.");
                        return;
                    }
                    if (state == STATE_PREFIX
                            || state == STATE_NAME
                            || state == STATE_INDEX_END
                            || state == STATE_URI_END) {

                        // eof path element
                        if (name == null) {
                            if (wasSlash) {
                                listener.error('\'' + jcrPath + "' is not a valid path: " +
                                        "Trailing slashes not allowed in prefixes and names.");
                                return;
                            }
                            name = jcrPath.substring(lastPos, pos - 1);
                        }

                        JcrNameParser.parse(name, listener);
                        if (!listener.index(index)) {
                            return;
                        }
                        state = STATE_PREFIX_START;
                        lastPos = pos;
                        name = null;
                        index = 0;
                    } else if (state == STATE_IDENTIFIER) {
                        if (c == EOF) {
                            // eof identifier reached                            
                            if (jcrPath.charAt(pos - 2) != ']') {
                                listener.error('\'' + jcrPath + "' is not a valid path: " +
                                        "Unterminated identifier segment.");
                                return;
                            }
                            String identifier = jcrPath.substring(lastPos, pos - 2);
                            if (!listener.identifier(identifier)) {
                                return;
                            }
                            state = STATE_PREFIX_START;
                            lastPos = pos;
                        }
                    } else if (state == STATE_DOT) {
                        if (!listener.current()) {
                            return;
                        }
                        lastPos = pos;
                        state = STATE_PREFIX_START;
                    } else if (state == STATE_DOTDOT) {
                        if (!listener.parent()) {
                            return;
                        }
                        lastPos = pos;
                        state = STATE_PREFIX_START;
                    } else if (state != STATE_URI
                            && !(state == STATE_PREFIX_START && c == EOF)) { // ignore trailing slash
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not a valid name character.");
                        return;
                    }
                    break;

                case '.':
                    if (state == STATE_PREFIX_START) {
                        state = STATE_DOT;
                    } else if (state == STATE_DOT) {
                        state = STATE_DOTDOT;
                    } else if (state == STATE_DOTDOT) {
                        state = STATE_PREFIX;
                    } else if (state == STATE_INDEX_END) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not valid after index. '/' expected.");
                        return;
                    }
                    break;

                case ':':
                    if (state == STATE_PREFIX_START) {
                        listener.error('\'' + jcrPath + "' is not a valid path. Prefix " +
                                "must not be empty");
                        return;
                    } else if (state == STATE_PREFIX) {
                        if (wasSlash) {
                            listener.error('\'' + jcrPath + "' is not a valid path: " +
                                    "Trailing slashes not allowed in prefixes and names.");
                            return;
                        }
                        state = STATE_NAME_START;
                        // don't reset the lastPos/pos since prefix+name are passed together to the NameResolver
                    } else if (state != STATE_IDENTIFIER && state != STATE_URI) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not valid name character");
                        return;
                    }
                    break;

                case '[':
                    if (state == STATE_PREFIX || state == STATE_NAME) {
                        if (wasSlash) {
                            listener.error('\'' + jcrPath + "' is not a valid path: " +
                                    "Trailing slashes not allowed in prefixes and names.");
                            return;
                        }
                        state = STATE_INDEX;
                        name = jcrPath.substring(lastPos, pos - 1);
                        lastPos = pos;
                    } else if (state != STATE_IDENTIFIER) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not a valid name character.");
                        return;
                    }
                    break;

                case ']':
                    if (state == STATE_INDEX) {
                        try {
                            index = Integer.parseInt(jcrPath.substring(lastPos, pos - 1));
                        } catch (NumberFormatException e) {
                            listener.error('\'' + jcrPath + "' is not a valid path. " +
                                    "NumberFormatException in index: " +
                                    jcrPath.substring(lastPos, pos - 1));
                            return;
                        }
                        if (index < 0) {
                            listener.error('\'' + jcrPath + "' is not a valid path. " +
                                    "Index number invalid: " + index);
                            return;
                        }
                        state = STATE_INDEX_END;
                    } else if (state != STATE_IDENTIFIER) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not a valid name character.");
                        return;
                    }
                    break;

                case ' ':
                    if (state == STATE_PREFIX_START || state == STATE_NAME_START) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not valid name start");
                        return;
                    } else if (state == STATE_INDEX_END) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not valid after index. '/' expected.");
                        return;
                    } else if (state == STATE_DOT || state == STATE_DOTDOT) {
                        state = STATE_PREFIX;
                    }
                    break;

                case '\t':
                    if (state != STATE_IDENTIFIER) {
                        listener.error('\'' + jcrPath + "' is not a valid path. " +
                                "Whitespace not a allowed in name.");
                        return;
                    }
                    break;
                case '*':
                case '|':
                    if (state != STATE_IDENTIFIER) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not a valid name character.");
                        return;
                    }
                    break;
                case '{':
                    if (state == STATE_PREFIX_START && lastPos == pos-1) {
                        // '{' marks the start of a uri enclosed in an expanded name
                        // instead of the usual namespace prefix, if it is
                        // located at the beginning of a new segment.
                        state = STATE_URI;
                    } else if (state == STATE_NAME_START || state == STATE_DOT || state == STATE_DOTDOT) {
                        // otherwise it's part of the local name
                        state = STATE_NAME;
                    }
                    break;

                case '}':
                    if (state == STATE_URI) {
                        state = STATE_URI_END;
                    }
                    break;

                default:
                    if (state == STATE_PREFIX_START || state == STATE_DOT || state == STATE_DOTDOT) {
                        state = STATE_PREFIX;
                    } else if (state == STATE_NAME_START) {
                        state = STATE_NAME;
                    } else if (state == STATE_INDEX_END) {
                        listener.error('\'' + jcrPath + "' is not a valid path. '" + c +
                                "' not valid after index. '/' expected.");
                        return;
                    }
            }
            wasSlash = c == ' ';
        }
    }

}
